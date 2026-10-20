package dev.dragonsnake9000.utils;

import meteordevelopment.meteorclient.events.render.*;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import net.minecraft.util.math.*;
import java.util.*;
import java.util.function.*;

/** Uniform opt-in diagnostics. Geometry is observational and never changes detection or path rules. */
final class DebugView {
    static final List<DebugView> ALL=new ArrayList<>();
    final Module owner;
    final Supplier<String> text;
    final Consumer<Render3DEvent> render;
    private final Setting<Boolean> show;
    private final Setting<Integer> opacity;
    private final boolean previewWhenInactive;
    DebugView(Module owner,Settings settings,Supplier<String> text,Consumer<Render3DEvent> render) {
        this(owner,settings,text,render,false);
    }
    DebugView(Module owner,Settings settings,Supplier<String> text,Consumer<Render3DEvent> render,boolean previewWhenInactive) {
        this.owner=owner;this.text=text;this.render=render;
        this.previewWhenInactive=previewWhenInactive;
        settings.getDefaultGroup();
        SettingGroup group=settings.createGroup("Diagnostics");
        show=group.add(new BoolSetting.Builder().name("show-hidden-info").description(previewWhenInactive
            ? "Preview planned grid sites even with Roof Lighting off or no supplies. Visuals only; does not start placement or refilling."
            : "Show active checks, routing geometry and a compact status overlay. Visuals only.").defaultValue(false).build());
        opacity=group.add(new IntSetting.Builder().name("diagnostic-opacity").description("Overlay fill opacity; outlines are brighter.").defaultValue(20).range(0,100).sliderRange(0,100).visible(show::get).build());
        ALL.add(this);
    }
    boolean enabled() { return (owner.isActive() || previewWhenInactive) && show.get() && opacity.get()>0; }
    Color line(int r,int g,int b) { return new Color(r,g,b,Math.min(255,opacity.get()*8)); }
    Color fill(int r,int g,int b) { return new Color(r,g,b,(int)(opacity.get()*2.55)); }
    void box(Render3DEvent e,BlockPos pos,int r,int g,int b) { e.renderer.box(pos,fill(r,g,b),line(r,g,b),ShapeMode.Both,0); }
    void line(Render3DEvent e,Vec3d from,Vec3d to,Color color) { e.renderer.line(from.x,from.y,from.z,to.x,to.y,to.z,color); }
    void circle(Render3DEvent e,double x,double y,double z,double radius,Color color) {
        for (int i=0;i<96;i++) {
            double a=i*Math.PI*2/96,b=(i+1)*Math.PI*2/96;
            e.renderer.line(x+Math.cos(a)*radius,y,z+Math.sin(a)*radius,x+Math.cos(b)*radius,y,z+Math.sin(b)*radius,color);
        }
    }
    void cone(Render3DEvent e,Vec3d origin,Vec3d direction,double length,double halfAngle,Color color) {
        Vec3d axis=direction.normalize();
        Vec3d u=axis.crossProduct(Math.abs(axis.y)>.9 ? new Vec3d(1,0,0) : new Vec3d(0,1,0)).normalize();
        Vec3d v=axis.crossProduct(u).normalize();
        Vec3d center=origin.add(axis.multiply(length)); double radius=Math.tan(Math.toRadians(halfAngle))*length;
        for (int i=0;i<32;i++) {
            double a=i*Math.PI*2/32,b=(i+1)*Math.PI*2/32;
            Vec3d first=center.add(u.multiply(Math.cos(a)*radius)).add(v.multiply(Math.sin(a)*radius));
            Vec3d second=center.add(u.multiply(Math.cos(b)*radius)).add(v.multiply(Math.sin(b)*radius));
            line(e,first,second,color); if (i%4==0) line(e,origin,first,color);
        }
        line(e,origin,center,color);
    }
}
