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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws the slot outline inside Neo ECO AE's storage subsystem host GUI.
 *
 * <p>That GUI is an LDLib2 {@code ModularUI}, not a vanilla container screen, so AE2's
 * {@code AEBaseScreen#renderSlot} hook cannot see it. Everything here goes through reflection:
 * linking against LDLib2 or the addon at compile time would make the repository unbuildable
 * without those jars.
 *
 * <p>LDLib2 has two screen flavours and the storage host uses the container-backed one, where the
 * {@code ModularUI} hangs off the menu rather than the screen, so the accessor is discovered per
 * screen class instead of assumed.
 *
 * <p>The data chain is: screen -> {@code ModularUI} -> all elements -> the {@code StorageCellGrid};
 * each child {@code CellSlot} carries a private {@code index} that selects an entry from the synced
 * {@code StoragePanelState.cells()} page, and every entry exposes the {@code BlockPos} of the drive
 * it belongs to. Matching is therefore positional and independent of the addon's ordering.
 */
public final class EcoStorageOverlay {

    private static final String GRID_CLASS =
            "cn.dancingsnow.neoecoae.gui.storage.StorageCellGrid";
    private static final String SLOT_CLASS =
            "cn.dancingsnow.neoecoae.gui.storage.StorageCellGrid$CellSlot";

    private static final int BORDER = 0xFFFFFFFF;
    private static final int HINT = 0xFFFFD54F;

    private static boolean resolved;
    private static String resolveError = "";
    private static String lastProbe = "";

    private static final Map<Class<?>, Optional<Object>> ACCESSORS = new ConcurrentHashMap<>();

    private static Method getAllElements;
    private static Field uiField;
    private static Field rootField;
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
        Screen screen = event.getScreen();
        if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
            // Container screens fire ContainerScreenEvent.Render.Foreground *after* the LDLib2
            // panel has been drawn; drawing here would put the overlay underneath it.
            return;
        }
        render(screen, event.getGuiGraphics());
    }

    public static void onContainerForeground(
            net.neoforged.neoforge.client.event.ContainerScreenEvent.Render.Foreground event) {
        render(event.getContainerScreen(), event.getGuiGraphics());
    }

    private static void render(Screen screen, GuiGraphics graphics) {
        if (!HighlightState.isLocked() || !HighlightState.hasRefs()) {
            return;
        }
        if (screen == null || graphics == null) {
            return;
        }

        if (!resolved) {
            resolve();
        }
        if (getAllElements == null) {
            probe("resolve failed: " + resolveError);
            return;
        }

        Object ui = modularUI(screen);
        if (ui == null) {
            probe("screen=" + screen.getClass().getName() + " -> no ModularUI");
            return;
        }

        try {
            if (!(getAllElements.invoke(ui) instanceof List<?> elements)) {
                probe("getAllElements returned no list");
                return;
            }
            float originX = (Float) getLeftPos.invoke(ui);
            float originY = (Float) getTopPos.invoke(ui);

            List<CellRef> refs = HighlightState.refs();

            var grids = new java.util.ArrayList<Object>();
            var seen = java.util.Collections.newSetFromMap(
                    new java.util.IdentityHashMap<Object, Boolean>());

            int registered = 0;
            for (Object element : elements) {
                if (element != null && element.getClass().getName().equals(GRID_CLASS)
                        && seen.add(element)) {
                    grids.add(element);
                    registered++;
                }
            }

            // getAllElements() only contains elements the ModularUI was told about; walking the
            // root element as well makes this independent of how the panel was built.
            int fromTree = 0;
            Object root = rootElement(ui);
            if (root != null) {
                fromTree = collectGrids(root, grids, seen, 0);
            }

            for (Object grid : grids) {
                drawForGrid(graphics, grid, refs, originX, originY);
            }
            probe("elements=" + elements.size() + " gridsRegistered=" + registered
                    + " gridsInTree=" + fromTree + " total=" + grids.size()
                    + " origin=" + Math.round(originX) + "," + Math.round(originY)
                    + " refs=" + refs.size() + " refPos=" + describe(refs)
                    + (grids.isEmpty() ? " sample=" + sampleClasses(elements) : ""));
        } catch (Throwable t) {
            probe("render failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static Object rootElement(Object modularUI) {
        try {
            Object ui = uiField.get(modularUI);
            return ui == null ? null : rootField.get(ui);
        } catch (Throwable t) {
            return null;
        }
    }

    private static int collectGrids(Object element, List<Object> out, java.util.Set<Object> seen,
            int depth) throws Exception {
        if (element == null || depth > 32 || !seen.add(element)) {
            return 0;
        }
        int found = 0;
        if (element.getClass().getName().equals(GRID_CLASS)) {
            out.add(element);
            found++;
        }
        if (getChildren.invoke(element) instanceof List<?> children) {
            for (Object child : children) {
                found += collectGrids(child, out, seen, depth + 1);
            }
        }
        return found;
    }

    /**
     * Resolves the {@code ModularUI} for a screen.
     *
     * <p>Plain LDLib2 screens expose {@code getModularUI()}; container-backed ones keep it on
     * {@code ModularUIContainerMenu}, reachable through the screen's {@code menu} field.
     */
    private static Object modularUI(Screen screen) {
        Object accessor = ACCESSORS.computeIfAbsent(screen.getClass(), EcoStorageOverlay::findAccessor)
                .orElse(null);
        if (accessor == null) {
            return null;
        }
        try {
            if (accessor instanceof Method method) {
                return method.invoke(screen);
            }
            if (accessor instanceof Field field) {
                return field.get(screen);
            }
            if (accessor instanceof MenuAccessor menu) {
                Object container = menu.menuField.get(screen);
                return container == null ? null : menu.getter.invoke(container);
            }
        } catch (Throwable t) {
            probe("modularUI access failed: " + t);
        }
        return null;
    }

    private static Optional<Object> findAccessor(Class<?> type) {
        if (!type.getName().startsWith("com.lowdragmc.lowdraglib2.gui.holder.")) {
            return Optional.empty();
        }
        try {
            return Optional.of(type.getMethod("getModularUI"));
        } catch (Throwable ignored) {
            // container-backed screens do not have it
        }
        try {
            return Optional.of(type.getField("modularUI"));
        } catch (Throwable ignored) {
            // not a plain field either
        }
        try {
            Field menuField = null;
            for (Class<?> c = type; c != null; c = c.getSuperclass()) {
                try {
                    menuField = c.getDeclaredField("menu");
                    break;
                } catch (Throwable ignored) {
                    // keep walking up the hierarchy
                }
            }
            if (menuField == null) {
                return Optional.empty();
            }
            menuField.setAccessible(true);
            Method getter = Class.forName("com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu")
                    .getMethod("getModularUI");
            return Optional.of(new MenuAccessor(menuField, getter));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    private record MenuAccessor(Field menuField, Method getter) {
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
            if (seen.length() < 140) {
                seen.append(pos.getX()).append(',').append(pos.getY()).append(',')
                        .append(pos.getZ()).append(' ');
            }

            for (CellRef ref : refs) {
                if (ref.pos().equals(pos)) {
                    drawFrame(graphics, child, originX, originY);
                    probe("matched index=" + index + " pos=" + pos.toShortString()
                            + " cells=" + cells.size() + " children=" + children.size());
                    return;
                }
            }
        }

        probe("no match; cells=" + cells.size() + " children=" + children.size()
                + " seen=" + seen);
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

        probe("frame x=" + x + " y=" + y + " w=" + w + " h=" + h
                + " uiOrigin=" + Math.round(originX) + "," + Math.round(originY)
                + " elementPose=" + Math.round(pose.m30()) + "," + Math.round(pose.m31()));

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

    private static String sampleClasses(List<?> elements) {
        var out = new java.util.LinkedHashSet<String>();
        for (Object element : elements) {
            if (element == null) {
                continue;
            }
            String name = element.getClass().getName();
            out.add(name.substring(name.lastIndexOf('.') + 1));
            if (out.size() >= 12) {
                break;
            }
        }
        return String.join("|", out);
    }

    private static String describe(List<CellRef> refs) {        var out = new StringBuilder();
        for (CellRef ref : refs) {
            if (out.length() > 90) {
                break;
            }
            out.append('(').append(ref.pos().getX()).append(',').append(ref.pos().getY())
                    .append(',').append(ref.pos().getZ()).append(") ");
        }
        return out.toString().trim();
    }

    /** Logs only when the summary changes, so an open GUI does not spam the log. */
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
            uiField = modularUI.getField("ui");

            Class<?> ui = Class.forName("com.lowdragmc.lowdraglib2.gui.ui.UI");
            rootField = ui.getField("rootElement");

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
