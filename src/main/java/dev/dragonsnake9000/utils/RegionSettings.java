package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.*;
import meteordevelopment.meteorclient.gui.widgets.input.*;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import net.minecraft.util.math.BlockPos;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Persistent settings plus coordinate controls whose slider range changes with a preset. */
final class RegionSettings {
    enum Mode { Y_Only, XYZ }
    enum Order { Top_Down, Bottom_Up }
    enum HorizontalDirection { Center_Outward, Edges_Inward }
    private final Setting<Boolean> horizontal;
    private final Setting<Boolean> showBox;
    private final Setting<Integer> opacity, advance;
    private final Setting<HorizontalDirection> horizontalDirection;
    enum Preset {
        OneK("1k", 1000), FiveK("5k", 5000), TenK("10k", 10000), HundredK("100k", 100000),
        TwoFiftySixK("256k", 256000), OneMillion("1mil", 1000000), ThirtyMillion("30mil", 30000000);
        final String label; final int limit;
        Preset(String label, int limit) { this.label = label; this.limit = limit; }
    }
    private final Setting<Boolean> enabled;
    private final Setting<Mode> mode;
    private final Setting<Boolean> ordered, returnInside;
    private final Setting<Order> order;
    private final Setting<Boolean> contours;
    private final Setting<Integer> band;
    private final Setting<Preset> preset;
    private final Setting<Integer> x1, y1, z1, x2, y2, z2;
    private String captureStatus = "";
    RegionSettings(Settings settings) {
        SettingGroup group = settings.createGroup("Stay in region");
        enabled = group.add(new BoolSetting.Builder().name("stay-in-region").description("Constrain targets and Baritone movement to the selected feet-coordinate region.").defaultValue(false).build());
        mode = group.add(new EnumSetting.Builder<Mode>().name("region-mode").defaultValue(Mode.Y_Only).build());
        showBox = group.add(new BoolSetting.Builder().name("show-region-box").description("Draw the selected region even while the pathfinder is off.").defaultValue(false).build());
        opacity = group.add(new IntSetting.Builder().name("region-opacity").description("Region fill opacity in percent; edges are brighter. Zero hides both.").defaultValue(12).range(0, 100).sliderRange(0, 100).visible(showBox::get).build());
        advance = group.add(new IntSetting.Builder().name("horizontal-goal-advance").description("Aim this many blocks beyond a moss boundary into exposed targets during horizontal work.").defaultValue(3).range(0, 8).sliderRange(0, 8).build());
        returnInside = group.add(new BoolSetting.Builder().name("return-to-region").description("Notify and navigate into a nearby selected region using the larger scan radius.").defaultValue(true).build());
        ordered = group.add(new BoolSetting.Builder().name("vertical-work-order").description("Prefer the highest or lowest eligible scanned layer before distance.").defaultValue(false).build());
        horizontal = group.add(new BoolSetting.Builder().name("horizontal-work-order").description("Order loaded targets by rectangular rings using the selected X/Z corners. Combines with vertical layers or bands.").defaultValue(false).build());
        horizontalDirection = group.add(new EnumSetting.Builder<HorizontalDirection>().name("horizontal-direction").defaultValue(HorizontalDirection.Edges_Inward).visible(horizontal::get).build());
        order = group.add(new EnumSetting.Builder<Order>().name("work-direction").description("Top Down descends; Bottom Up ascends. Limited to loaded scan ranges.").defaultValue(Order.Top_Down).visible(ordered::get).build());
        contours = group.add(new BoolSetting.Builder().name("surface-contours").description("Sweep around a stable height band and protect whitelisted blocks from tunneling during normal work.").defaultValue(true).visible(ordered::get).build());
        band = group.add(new IntSetting.Builder().name("surface-band-height").description("Finish nearby exposed surfaces in this many Y levels before advancing vertically.").defaultValue(9).range(1, 64).sliderRange(1, 12).visible(() -> ordered.get() && contours.get()).build());
        preset = group.add(new EnumSetting.Builder<Preset>().name("coordinate-preset").defaultValue(Preset.FiveK).visible(() -> false).build());
        x1 = coordinate(group, "from-x", -5000, -30000000, 30000000);
        y1 = coordinate(group, "min-y", 60, -64, 320);
        z1 = coordinate(group, "from-z", -5000, -30000000, 30000000);
        x2 = coordinate(group, "to-x", 5000, -30000000, 30000000);
        y2 = coordinate(group, "max-y", 320, -64, 320);
        z2 = coordinate(group, "to-z", 5000, -30000000, 30000000);
    }
    private Setting<Integer> coordinate(SettingGroup group, String name, int value, int min, int max) {
        return group.add(new IntSetting.Builder().name(name).defaultValue(value).range(min, max).visible(() -> false).build());
    }
    RegionBounds snapshot() {
        if (!enabled.get()) return null;
        boolean xyz = mode.get() == Mode.XYZ;
        return RegionBounds.normalized(xyz ? x1.get() : -30000000, y1.get(), xyz ? z1.get() : -30000000,
            xyz ? x2.get() : 30000000, y2.get(), xyz ? z2.get() : 30000000);
    }
    int orderSign() { return !ordered.get() ? 0 : order.get() == Order.Top_Down ? -1 : 1; }
    boolean returnInside() { return returnInside.get(); }
    boolean contours() { return ordered.get() && contours.get(); }
    int bandHeight() { return band.get(); }
    boolean xyzRegion() { return enabled.get() && mode.get() == Mode.XYZ; }
    double centerX() { return ((double) x1.get() + x2.get() + 1) / 2; }
    double centerZ() { return ((double) z1.get() + z2.get() + 1) / 2; }
    int goalAdvance() { return horizontal() ? advance.get() : 0; }
    void render(meteordevelopment.meteorclient.events.render.Render3DEvent event) {
        if (!showBox.get() || mc.world == null || opacity.get() == 0) return;
        var bounds = RegionBounds.normalized(x1.get(), y1.get(), z1.get(), x2.get(), y2.get(), z2.get());
        int alpha = Math.round(opacity.get() * 2.55f);
        var fill = new meteordevelopment.meteorclient.utils.render.color.Color(255, 170, 0, alpha);
        var line = new meteordevelopment.meteorclient.utils.render.color.Color(255, 195, 65, Math.min(255, alpha * 4));
        if (mode.get() == Mode.XYZ) {
            event.renderer.box(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX() + 1.0, bounds.maxY() + 1.0, bounds.maxZ() + 1.0,
                fill, line, meteordevelopment.meteorclient.renderer.ShapeMode.Both, 0);
        } else {
            var camera = mc.gameRenderer.getCamera().getPos();
            double range = mc.options.getViewDistance().getValue() * 16.0;
            for (double y : new double[] {bounds.minY(), bounds.maxY() + 1.0}) {
                event.renderer.quadHorizontal(camera.x - range, y, camera.z - range, camera.x + range, camera.z + range, fill);
                event.renderer.boxLines(camera.x - range, y, camera.z - range, camera.x + range, y, camera.z + range, line, 0);
            }
        }
    }
    boolean horizontal() { return enabled.get() && horizontal.get(); }
    int horizontalSignature() { return java.util.Objects.hash(horizontal(), horizontalDirection.get(), x1.get(), z1.get(), x2.get(), z2.get()); }
    double horizontalRank(int x, int z) {
        if (!horizontal()) return 0;
        double fraction = HorizontalOrder.fraction(x, z, x1.get(), z1.get(), x2.get(), z2.get());
        // Four-block rings retain local distance/contour preferences within a ring.
        double rings = Math.max(1, Math.max(Math.abs((double) x2.get() - x1.get()), Math.abs((double) z2.get() - z1.get())) / 8);
        double rank = Math.floor(fraction * rings) / rings;
        return horizontalDirection.get() == HorizontalDirection.Center_Outward ? rank : -rank;
    }
    WWidget widget(GuiTheme theme) {
        WVerticalList root = theme.verticalList();
        rebuild(root, theme);
        return root;
    }
    private void rebuild(WVerticalList root, GuiTheme theme) {
        root.clear();
        WButton first = root.add(theme.button("Set Camera Pos1(Min)")).widget();
        WButton second = root.add(theme.button("Set Camera Pos2(Max)")).widget();
        first.action = () -> { captureCamera(true); rebuild(root, theme); };
        second.action = () -> { captureCamera(false); rebuild(root, theme); };
        if (!captureStatus.isEmpty()) root.add(theme.label(captureStatus));
        root.add(theme.label("Slider Coordinate Range"));
        WHorizontalList presets = root.add(theme.horizontalList()).widget();
        for (Preset option : Preset.values()) {
            presets.add(theme.label(option.label));
            WCheckbox check = presets.add(theme.checkbox(preset.get() == option)).widget();
            check.action = () -> {
                preset.set(option); // One enum is the source of truth: checkboxes cannot conflict.
                for (Setting<Integer> coordinate : java.util.List.of(x1, z1, x2, z2)) {
                    coordinate.set(Math.max(-option.limit, Math.min(option.limit, coordinate.get())));
                }
                rebuild(root, theme);
            };
        }
        int limit = preset.get().limit;
        row(root, theme, "From X", x1, -limit, limit);
        row(root, theme, "Min Y", y1, -64, 320);
        row(root, theme, "From Z", z1, -limit, limit);
        row(root, theme, "To X", x2, -limit, limit);
        row(root, theme, "Max Y", y2, -64, 320);
        row(root, theme, "To Z", z2, -limit, limit);
    }
    private void row(WVerticalList root, GuiTheme theme, String name, Setting<Integer> setting, int min, int max) {
        WHorizontalList row = root.add(theme.horizontalList()).expandX().widget();
        row.add(theme.label(name)).minWidth(65);
        row.add(new CoordinateEdit(setting, min, max)).expandX();
    }

    private void captureCamera(boolean first) {
        if (mc.world == null || mc.player == null) {
            captureStatus = "Join a world before capturing a camera position.";
            return;
        }
        // Read the rendered camera itself, not the player/camera entity: Freecam can detach it.
        BlockPos position = BlockPos.ofFloored(mc.gameRenderer.getCamera().getPos());
        int y = Math.max(-64, Math.min(320, position.getY()));
        (first ? y1 : y2).set(y);
        boolean xyz = mode.get() == Mode.XYZ;
        if (xyz) {
            (first ? x1 : x2).set(Math.max(-30000000, Math.min(30000000, position.getX())));
            (first ? z1 : z2).set(Math.max(-30000000, Math.min(30000000, position.getZ())));
            // Expand the slider preset if necessary instead of clipping a Freecam capture to 5k.
            int required = Math.max(Math.max(Math.abs(x1.get()), Math.abs(x2.get())),
                Math.max(Math.abs(z1.get()), Math.abs(z2.get())));
            if (required > preset.get().limit) for (Preset option : Preset.values()) {
                if (required <= option.limit) { preset.set(option); break; }
            }
        }
        captureStatus = "Position " + (first ? "1" : "2") + ": "
            + (xyz ? (first ? x1.get() : x2.get()) + ", " + y + ", " + (first ? z1.get() : z2.get()) : "Y = " + y)
            + (y != position.getY() ? " (Y limited to -64 through 320)" : "");
    }

    /** Standard Meteor text/slider widgets, with middle-click explicitly routed to text focus. */
    private static final class CoordinateEdit extends WHorizontalList {
        private final Setting<Integer> setting;
        private final int min, max;
        private WTextBox text;
        private WSlider slider;
        CoordinateEdit(Setting<Integer> setting, int min, int max) { this.setting = setting; this.min = min; this.max = max; }
        @Override public void init() {
            text = add(theme.textBox(Integer.toString(setting.get()))).minWidth(105).widget();
            slider = add(theme.slider(setting.get(), min, max)).minWidth(240).expandX().widget();
            slider.action = () -> { setting.set((int) Math.round(slider.get())); text.set(Integer.toString(setting.get())); };
            text.actionOnUnfocused = () -> {
                try { setting.set(Math.max(min, Math.min(max, Integer.parseInt(text.get().trim())))); }
                catch (NumberFormatException ignored) { /* Keep the previous valid coordinate. */ }
                text.set(Integer.toString(setting.get()));
                slider.set(setting.get());
            };
        }
        @Override public boolean mouseClicked(double x, double y, int button, boolean used) {
            if (!used && button == 2 && slider.mouseOver) { text.setFocused(true); return true; }
            return super.mouseClicked(x, y, button, used);
        }
    }
}
