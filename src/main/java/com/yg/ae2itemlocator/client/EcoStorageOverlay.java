package com.yg.ae2itemlocator.client;

import com.yg.ae2itemlocator.AE2ItemLocator;
import com.yg.ae2itemlocator.common.CellRef;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Draws the slot outline inside Neo ECO AE's storage subsystem host GUI.
 *
 * <p>That GUI is an LDLib2 {@code ModularUI}, not a vanilla container screen, so AE2's
 * {@code AEBaseScreen#renderSlot} hook cannot see it and neither can {@code AbstractContainerScreen}.
 * Everything here therefore goes through reflection: attaching this mod to LDLib2 or to the addon at
 * compile time would make the repository unbuildable without those jars.
 *
 * <p>The data chain is: screen -> {@code ModularUI} -> all elements -> the {@code StorageCellGrid};
 * each child {@code CellSlot} carries a private {@code index} that selects an entry from the synced
 * {@code StoragePanelState.cells()} page, and every entry exposes the {@code BlockPos} of the drive
 * it belongs to. So the mapping is positional and independent of how the addon orders its list.
 */
public final class EcoStorageOverlay {

    private static final String SCREEN_CLASS =
            "com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen";
    private static final String CONTAINER_SCREEN_CLASS =
            "com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen";
    private static final String GRID_CLASS =
            "cn.dancingsnow.neoecoae.gui.storage.StorageCellGrid";
    private static final String SLOT_CLASS =
            "cn.dancingsnow.neoecoae.gui.storage.StorageCellGrid$CellSlot";

    private static final int BORDER = 0xFFFFFFFF;
    private static final int HINT = 0xFFFFD54F;

    private static boolean resolved;
    private static String resolveError = "";
    private static String lastProbe = "";

    /** Screens come in two flavours (plain and container-backed), so match by capability. */
    private static final java.util.Map<Class<?>, java.util.Optional<Method>> SCREEN_METHODS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static Method modularUIMethod(Screen screen) {
        return SCREEN_METHODS.computeIfAbsent(screen.getClass(), type -> {
            String name = type.getName();
            if (!name.startsWith("com.lowdragmc.lowdraglib2.gui.holder.")) {
                return java.util.Optional.empty();
            }
            try {
                return java.util.Optional.of(type.getMethod("getModularUI"));
            } catch (Throwable t) {
                return java.util.Optional.empty();
            }
        }).orElse(null);
    }

    private static Method getModularUI;
    private static Method getAllElements;
    private static Method getLeftPos;
    private static Method getTopPos;
    private static Method getChildren;
    private static Method getLocalToWorldPose;
    private static Method getSizeWidth;
    private static Method getSizeHeight;
    private static Field syncField;
    private static Method panelState;
    private static Method panelCells;
    private static Method panelOffset;
    private static Method panelCount;
    private static Field slotIndex;
    private static Method entryPosition;

    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        if (!HighlightState.isLocked() || !HighlightState.hasRefs()) {
            return;
        }

        Screen screen = event.getScreen();
        if (screen == null) {
            return;
        }

        if (!resolved) {
            resolve();
        }
        if (getAllElements == null) {
            probe("resolve failed: " + resolveError);
            return;
        }
        Method modularUI = modularUIMethod(screen);
        if (modularUI == null) {
            probe("screen=" + screen.getClass().getName() + " -> not a modular UI, skipped");
            return;
        }

        try {
            Object ui = modularUI.invoke(screen);
            if (ui == null) {
                probe("modularUI is null");
                return;
            }
            if (!(getAllElements.invoke(ui) instanceof List<?> elements)) {
                probe("getAllElements returned no list");
                return;
            }
            float originX = (Float) getLeftPos.invoke(ui);
            float originY = (Float) getTopPos.invoke(ui);

            GuiGraphics graphics = event.getGuiGraphics();
            List<CellRef> refs = HighlightState.refs();

            int grids = 0;
            for (Object element : elements) {
                if (element == null || !element.getClass().getName().equals(GRID_CLASS)) {
                    continue;
                }
                grids++;
                drawForGrid(graphics, element, refs, originX, originY);
            }
            probe("elements=" + elements.size() + " grids=" + grids
                    + " origin=" + Math.round(originX) + "," + Math.round(originY)
                    + " refs=" + refs.size() + " refPos=" + describe(refs)
                    + " screen=" + screen.getClass().getSimpleName());
        } catch (Throwable t) {
            probe("render failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static void drawForGrid(GuiGraphics graphics, Object grid, List<CellRef> refs,
            float originX, float originY) throws Exception {
        Object state = panelState.invoke(syncField.get(grid));
        if (state == null) {
            probe("panel state is null");
            return;
        }
        if (!(panelCells.invoke(state) instanceof List<?> cells) || cells.isEmpty()) {
            probe("cells is empty");
            return;
        }
        if (!(getChildren.invoke(grid) instanceof List<?> children)) {
            probe("grid has no children");
            return;
        }

        StringBuilder seen = new StringBuilder();
        for (Object child : children) {
            if (child == null || !child.getClass().getName().equals(SLOT_CLASS)) {
                continue;
            }
            int index = slotIndex.getInt(child);
            if (index < 0 || index >= cells.size()) {
                continue;
            }
            BlockPos pos = (BlockPos) entryPosition.invoke(cells.get(index));
            if (pos == null) {
                continue;
            }
            if (seen.length() < 120) {
                seen.append(pos.getX()).append(',').append(pos.getY()).append(',')
                        .append(pos.getZ()).append(' ');
            }

            for (CellRef ref : refs) {
                if (ref.pos().equals(pos)) {
                    drawFrame(graphics, child, originX, originY);
                    probe("matched slot index=" + index + " pos=" + pos.toShortString()
                            + " cells=" + cells.size() + " children=" + children.size());
                    return;
                }
            }
        }

        probe("no match on this page; cells=" + cells.size() + " children=" + children.size()
                + " first=" + seen);
        drawPageHint(graphics, grid, state, cells, refs, originX, originY);
    }

    private static void drawFrame(GuiGraphics graphics, Object slot, float originX, float originY)
            throws Exception {
        Matrix4f pose = (Matrix4f) getLocalToWorldPose.invoke(slot);
        float width = (Float) getSizeWidth.invoke(slot);
        float height = (Float) getSizeHeight.invoke(slot);

        int x = Math.round(originX + pose.m30());
        int y = Math.round(originY + pose.m31());
        int w = Math.max(2, Math.round(width));
        int h = Math.max(2, Math.round(height));

        graphics.fill(x, y, x + w, y + 1, BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, BORDER);
        graphics.fill(x, y, x + 1, y + h, BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, BORDER);
    }

    /**
     * The controller GUI pages through its cells, so when the target drive sits on another page the
     * best we can do is say which page it is on.
     */
    private static void drawPageHint(GuiGraphics graphics, Object grid, Object state, List<?> cells,
            List<CellRef> refs, float originX, float originY) throws Exception {
        int pageSize = cells.size();
        if (pageSize <= 0) {
            return;
        }

        CellRef target = null;
        for (CellRef ref : refs) {
            if (ref.link() != null) {
                target = ref;
                break;
            }
        }
        if (target == null) {
            return;
        }

        int total = (Integer) panelCount.invoke(state);
        int offset = (Integer) panelOffset.invoke(state);
        int currentPage = offset / pageSize + 1;
        int pages = Math.max(1, (total + pageSize - 1) / pageSize);

        int targetPage = target.hostSlot() >= 0 ? target.hostSlot() / pageSize + 1 : -1;
        if (targetPage == currentPage) {
            return;
        }

        Component text = targetPage > 0
                ? Component.translatable("gui.ae2itemlocator.page_hint", targetPage, pages)
                : Component.translatable("gui.ae2itemlocator.page_hint_unknown", pages);

        Matrix4f pose = (Matrix4f) getLocalToWorldPose.invoke(grid);
        float height = (Float) getSizeHeight.invoke(grid);
        int x = Math.round(originX + pose.m30());
        int y = Math.round(originY + pose.m31() + height + 2f);

        Minecraft minecraft = Minecraft.getInstance();
        graphics.drawString(minecraft.font, text.copy().withStyle(ChatFormatting.YELLOW), x, y, HINT, true);
    }

    private static String describe(List<CellRef> refs) {
        var out = new StringBuilder();
        for (CellRef ref : refs) {
            if (out.length() > 90) {
                break;
            }
            out.append('(').append(ref.pos().getX()).append(',').append(ref.pos().getY())
                    .append(',').append(ref.pos().getZ()).append(") ");
        }
        return out.toString().trim();
    }

    /** Logs only when the summary changes, so a GUI open does not spam the log. */
    private static void probe(String message) {
        if (!message.equals(lastProbe)) {
            lastProbe = message;
            AE2ItemLocator.LOG.info("[ae2-disk-locator] eco-gui {}", message);
        }
    }

    private static synchronized void resolve() {
        resolved = true;
        try {
            Class<?> modularUI = Class.forName("com.lowdragmc.lowdraglib2.gui.ui.ModularUI");
            getAllElements = modularUI.getMethod("getAllElements");
            getLeftPos = modularUI.getMethod("getLeftPos");
            getTopPos = modularUI.getMethod("getTopPos");

            Class<?> element = Class.forName("com.lowdragmc.lowdraglib2.gui.ui.UIElement");
            getChildren = element.getMethod("getChildren");
            getLocalToWorldPose = element.getMethod("getLocalToWorldPose");
            getSizeWidth = element.getMethod("getSizeWidth");
            getSizeHeight = element.getMethod("getSizeHeight");

            Class<?> grid = Class.forName(GRID_CLASS);
            syncField = grid.getDeclaredField("sync");
            syncField.setAccessible(true);

            Class<?> sync = Class.forName("cn.dancingsnow.neoecoae.gui.storage.StoragePanelSync");
            panelState = sync.getDeclaredMethod("state");
            panelState.setAccessible(true);

            Class<?> state = Class.forName("cn.dancingsnow.neoecoae.gui.storage.StoragePanelState");
            panelCells = state.getMethod("cells");
            panelOffset = state.getMethod("cellOffset");
            panelCount = state.getMethod("cellCount");

            Class<?> slot = Class.forName(SLOT_CLASS);
            slotIndex = slot.getDeclaredField("index");
            slotIndex.setAccessible(true);

            Class<?> entry = Class.forName("cn.dancingsnow.neoecoae.gui.storage.StorageHostUI$CellEntry");
            entryPosition = entry.getMethod("position");
        } catch (Throwable t) {
            resolveError = "lookup: " + t;
            getAllElements = null;
        }
    }

    private EcoStorageOverlay() {
    }
}
