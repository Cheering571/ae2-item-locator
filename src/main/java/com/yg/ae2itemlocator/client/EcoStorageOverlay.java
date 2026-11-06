package com.yg.ae2itemlocator.client;

import com.yg.ae2itemlocator.AE2ItemLocator;
import com.yg.ae2itemlocator.common.CellRef;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws the slot outline inside Neo ECO AE's storage subsystem host GUI.
 *
 * <p>The panel is an LDLib2 {@code ModularUI} built from a JSON layout, so AE2's
 * {@code AEBaseScreen#renderSlot} hook cannot see it and {@code getAllElements()} does not
 * necessarily contain the paged cell grid. Everything here is reflective to keep the mod buildable
 * without LDLib2 or the addon on the classpath.
 *
 * <p>Drive-to-slot relationship: {@code StorageCellGrid} creates one {@code CellSlot} per grid
 * position, each holding an int {@code index}; {@code StorageCellGrid.cell(index)} returns
 * {@code StoragePanelState.cells().get(index)} and {@code CellEntry.position()} is the drive's block
 * position. A slot is therefore identified by asking the grid for the entry at the slot's own index
 * and comparing that position.
 *
 * <p>Drawing happens from {@code ContainerScreenEvent.Render.Foreground}, which fires at the end of
 * {@code AbstractContainerScreen#render} — after LDLib2 painted the panel in {@code renderBg}.
 */
public final class EcoStorageOverlay {

    private static final String GRID_CLASS =
            "cn.dancingsnow.neoecoae.gui.storage.StorageCellGrid";
    private static final String SLOT_SUFFIX = "CellSlot";

    private static final int HINT = 0xFFFFD54F;

    private static boolean resolved;
    private static String resolveError = "";
    private static String lastProbe = "";

    private static final Map<Class<?>, Optional<Object>> ACCESSORS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Optional<Method>> CELL_METHODS = new ConcurrentHashMap<>();

    private static Method getAllElements;
    private static Field uiField;
    private static Field rootField;
    private static Method getLeftPos;
    private static Method getTopPos;
    private static Method getChildren;
    private static Method getLocalToWorldPose;
    private static Method getPositionX;
    private static Method getPositionY;
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
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {
            return; // handled by the foreground hook, which runs after the LDLib2 panel
        }
        render(event.getScreen(), event.getGuiGraphics());
    }

    public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
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
            if (!(getAllElements.invoke(ui) instanceof List<?> registered)) {
                probe("getAllElements returned no list");
                return;
            }
            float originX = (Float) getLeftPos.invoke(ui);
            float originY = (Float) getTopPos.invoke(ui);
            List<CellRef> refs = HighlightState.refs();

            var grids = new ArrayList<Object>();
            Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());

            for (Object element : registered) {
                if (element != null && isGrid(element) && seen.add(element)) {
                    grids.add(element);
                }
            }

            Object root = rootElement(ui);
            if (root != null) {
                collectGrids(root, grids, seen, 0);
            }

            for (Object grid : grids) {
                drawForGrid(graphics, grid, refs, originX, originY);
            }

            probe("elements=" + registered.size() + " grids=" + grids.size()
                    + " origin=" + Math.round(originX) + "," + Math.round(originY)
                    + " refs=" + refs.size() + " refPos=" + describe(refs)
                    + (grids.isEmpty() ? " sample=" + sampleClasses(registered) : ""));
        } catch (Throwable t) {
            probe("render failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    /**
     * A grid is recognised structurally as well as by name: any element that offers an int
     * {@code cell(int)} method and has a {@code CellSlot} child is the cell grid, regardless of how
     * the JSON layout wrapped it.
     */
    private static boolean isGrid(Object element) {
        if (element.getClass().getName().equals(GRID_CLASS)) {
            return true;
        }
        if (cellMethod(element.getClass()) == null) {
            return false;
        }
        try {
            if (getChildren.invoke(element) instanceof List<?> children) {
                for (Object child : children) {
                    if (child != null && child.getClass().getName().endsWith(SLOT_SUFFIX)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // not a grid
        }
        return false;
    }

    private static Method cellMethod(Class<?> type) {
        return CELL_METHODS.computeIfAbsent(type, t -> {
            for (Class<?> c = t; c != null && c != Object.class; c = c.getSuperclass()) {
                try {
                    Method method = c.getDeclaredMethod("cell", int.class);
                    method.setAccessible(true);
                    return Optional.of(method);
                } catch (Throwable ignored) {
                    // keep walking up
                }
            }
            return Optional.empty();
        }).orElse(null);
    }

    private static Object rootElement(Object modularUI) {
        try {
            Object ui = uiField.get(modularUI);
            return ui == null ? null : rootField.get(ui);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void collectGrids(Object element, List<Object> out, Set<Object> seen, int depth) {
        if (element == null || depth > 32 || !seen.add(element)) {
            return;
        }
        if (isGrid(element)) {
            out.add(element);
        }
        try {
            if (getChildren.invoke(element) instanceof List<?> children) {
                for (Object child : children) {
                    collectGrids(child, out, seen, depth + 1);
                }
            }
        } catch (Throwable ignored) {
            // leaf
        }
    }

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
        Method cell = cellMethod(grid.getClass());
        if (cell == null) {
            probe("no cell(int) on " + grid.getClass().getName());
            return;
        }
        if (!(getChildren.invoke(grid) instanceof List<?> children)) {
            probe("grid has no children");
            return;
        }

        var seen = new StringBuilder();
        int slots = 0;
        for (Object child : children) {
            if (child == null || !child.getClass().getName().endsWith(SLOT_SUFFIX)) {
                continue;
            }
            slots++;
            int index = slotIndex.getInt(child);
            Object entry = cell.invoke(grid, index);
            if (entry == null) {
                continue;
            }
            BlockPos pos = (BlockPos) entryPosition.invoke(entry);
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
                            + " slots=" + slots);
                    return;
                }
            }
        }

        probe("no match; slots=" + slots + " seen=" + seen);
        drawPageHint(graphics, grid, refs, originX, originY);
    }

    private static void drawFrame(GuiGraphics graphics, Object slot, float originX, float originY)
            throws Exception {
        float width = (Float) getSizeWidth.invoke(slot);
        float height = (Float) getSizeHeight.invoke(slot);

        // getLocalToWorldPose() already resolves the whole ancestor chain into GUI space. Adding the
        // ModularUI origin shifted the frame by that origin (1.3.2); summing every ancestor's
        // position multiplied it and pushed the frame off-screen (1.3.3). The raw pose is correct.
        Matrix4f pose = (Matrix4f) getLocalToWorldPose.invoke(slot);
        int w = Math.max(2, Math.round(width));
        int h = Math.max(2, Math.round(height));
        int x = Math.round(pose.m30());
        int y = Math.round(pose.m31());

        // Insurance: never draw nothing because a coordinate went absurd.
        if (Math.abs(x) > 5000 || Math.abs(y) > 5000) {
            x = Math.round((Float) getPositionX.invoke(slot));
            y = Math.round((Float) getPositionY.invoke(slot));
        }

        probe("frame x=" + x + " y=" + y + " w=" + w + " h=" + h
                + " slotPos=" + Math.round((Float) getPositionX.invoke(slot)) + ","
                + Math.round((Float) getPositionY.invoke(slot))
                + " origin=" + Math.round(originX) + "," + Math.round(originY));

        // Same colours and geometry as the drive slot outline, so the configured slot colour
        // applies here too.
        int border = ClientConfig.slotColor();
        int fill = (border & 0xFFFFFF) | 0x33000000;

        graphics.fill(x, y, x + w, y + h, fill);
        graphics.fill(x, y, x + w, y + 1, border);
        graphics.fill(x, y + h - 1, x + w, y + h, border);
        graphics.fill(x, y, x + 1, y + h, border);
        graphics.fill(x + w - 1, y, x + w, y + h, border);
    }

    private static void drawPageHint(GuiGraphics graphics, Object grid, List<CellRef> refs,
            float originX, float originY) throws Exception {
        CellRef target = null;
        for (CellRef ref : refs) {
            if (ref.link() != null) {
                target = ref;
                break;
            }
        }
        if (target == null || syncField == null) {
            return;
        }

        Object state = panelState.invoke(syncField.get(grid));
        if (state == null || !(panelCells.invoke(state) instanceof List<?> cells) || cells.isEmpty()) {
            return;
        }

        int pageSize = cells.size();
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
        int x = Math.round(pose.m30());
        int y = Math.round(pose.m31() + 2f);

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
            getPositionX = element.getMethod("getPositionX");
            getPositionY = element.getMethod("getPositionY");
            getSizeWidth = element.getMethod("getSizeWidth");
            getSizeHeight = element.getMethod("getSizeHeight");

            Class<?> slot = Class.forName("cn.dancingsnow.neoecoae.gui.storage.StorageCellGrid$CellSlot");
            slotIndex = slot.getDeclaredField("index");
            slotIndex.setAccessible(true);

            Class<?> entry = Class.forName("cn.dancingsnow.neoecoae.gui.storage.StorageHostUI$CellEntry");
            entryPosition = entry.getMethod("position");

            // Optional: only used for the page hint.
            try {
                Class<?> grid = Class.forName(GRID_CLASS);
                Field field = grid.getDeclaredField("sync");
                field.setAccessible(true);
                syncField = field;

                Class<?> sync = Class.forName("cn.dancingsnow.neoecoae.gui.storage.StoragePanelSync");
                panelState = sync.getDeclaredMethod("state");
                panelState.setAccessible(true);

                Class<?> state = Class.forName("cn.dancingsnow.neoecoae.gui.storage.StoragePanelState");
                panelCells = state.getMethod("cells");
                panelOffset = state.getMethod("cellOffset");
                panelCount = state.getMethod("cellCount");
            } catch (Throwable ignored) {
                syncField = null;
            }
        } catch (Throwable t) {
            resolveError = "lookup: " + t;
            getAllElements = null;
        }
    }

    private EcoStorageOverlay() {
    }
}
