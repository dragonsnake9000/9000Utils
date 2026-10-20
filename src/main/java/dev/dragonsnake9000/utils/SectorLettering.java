package dev.dragonsnake9000.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.utils.render.color.Color;

/** Precomputed bold glyph spans, stretched into condensed lettering; no OS font needed in-game. */
final class SectorLettering {
    record Span(int row,int from,int to) {}
    record Glyph(int width,int height,List<Span> spans) {}
    private static final Map<Character,Glyph> GLYPHS=load();
    private static Map<Character,Glyph> load() {
        Map<Character,Glyph> result=new HashMap<>();
        try(var input=SectorLettering.class.getResourceAsStream("/assets/utils9000/sector-glyphs.txt")) {
            if(input==null)throw new IOException("Missing sector glyphs");
            var reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8));String line;
            while((line=reader.readLine())!=null) {
                String[] header=line.split(" ");List<Span> spans=new ArrayList<>();
                while((line=reader.readLine())!=null && !line.equals("end")) {
                    String[] part=line.split(" ");spans.add(new Span(Integer.parseInt(part[0]),Integer.parseInt(part[1]),Integer.parseInt(part[2])));
                }
                result.put(header[0].charAt(0),new Glyph(Integer.parseInt(header[1]),Integer.parseInt(header[2]),List.copyOf(spans)));
            }
        } catch(IOException e) { throw new IllegalStateException("Cannot load sector lettering",e); }
        return Map.copyOf(result);
    }
    static void draw(Renderer3D renderer,String text,double cx,double cz,Color color) {
        double gap=40,width=(900-gap*(text.length()-1))/text.length();
        for(int c=0;c<text.length();c++) {
            Glyph glyph=GLYPHS.get(text.charAt(c));
            double start=cx-450+c*(width+gap);
            for(Span span:glyph.spans()) {
                double x1=start+width*span.from()/glyph.width(),x2=start+width*span.to()/glyph.width();
                // Reverse Z: viewed from below, text is readable rather than mirrored from the top face.
                double z1=cz+425-850.0*span.row()/glyph.height(),z2=cz+425-850.0*(span.row()+1)/glyph.height();
                renderer.quadHorizontal(x1,599.98,z2,x2,z1,color);
            }
        }
    }
}
