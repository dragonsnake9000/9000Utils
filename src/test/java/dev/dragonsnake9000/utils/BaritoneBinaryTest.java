package dev.dragonsnake9000.utils;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import static org.junit.jupiter.api.Assertions.*;

/** Integration contracts against the shipped binary, whose implementation names are obfuscated. */
class BaritoneBinaryTest {
    @Test void directInputPauseHooksExistInPinnedBinary() throws Exception {
        Set<String> methods = new HashSet<>();
        read("baritone/utils/InputOverrideHandler", new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                methods.add(name + desc); return null;
            }
        });
        assertTrue(methods.contains("setInputForceState(Lbaritone/api/utils/input/Input;Z)V"));
        assertTrue(methods.contains("onTick(Lbaritone/api/event/events/TickEvent;)V"));
        assertTrue(methods.contains("clearAllKeys()V"));
    }
    @Test void miningIntentHookRunsBeforeRotationAndInputDispatch() throws Exception {
        List<String> order = new ArrayList<>();
        read("baritone/pathing/movement/Movement", new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                if (!name.equals("update")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitFieldInsn(int op, String owner, String name, String desc) {
                        if (op == Opcodes.GETFIELD && owner.equals("baritone/pathing/movement/MovementState")
                            && name.equals("a") && desc.equals("Lbaritone/pathing/movement/MovementState$MovementTarget;")) order.add("guard");
                    }
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if (owner.equals("java/util/Optional") && name.equals("ofNullable")) order.add("rotation");
                        if (owner.equals("java/util/Map") && name.equals("forEach")) order.add("inputs");
                    }
                };
            }
        });
        assertEquals(List.of("guard", "rotation", "inputs"), order);
    }
    private void read(String name, ClassVisitor visitor) throws Exception {
        try (ZipFile jar = new ZipFile(Path.of(System.getProperty("baritone.binary")).toFile())) {
            var entry = jar.getEntry(name + ".class");
            assertNotNull(entry, name);
            try (var stream = jar.getInputStream(entry)) { new ClassReader(stream).accept(visitor, 0); }
        }
    }
    @Test void searchRedirectHasExactlyOneMatchingCallSite() throws Exception {
        int[] calls = {0};
        read("baritone/pathing/calc/AStarPathFinder", new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                if (!name.equals("a") || !desc.equals("(JJ)Ljava/util/Optional;")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int op, String owner, String method, String descriptor, boolean itf) {
                        if (owner.equals("baritone/pathing/movement/Moves") && method.equals("a")
                            && descriptor.equals("(Lbaritone/pathing/movement/CalculationContext;IIILbaritone/utils/pathing/MutableMoveResult;)V")) calls[0]++;
                    }
                };
            }
        });
        assertEquals(1, calls[0]);
    }
    @Test void resultAccessorFieldsExistWithCorrectDescriptors() throws Exception {
        Set<String> fields = new HashSet<>();
        read("baritone/utils/pathing/MutableMoveResult", new ClassVisitor(Opcodes.ASM9) {
            @Override public FieldVisitor visitField(int access, String name, String desc, String sig, Object value) {
                fields.add(name + ":" + desc); return null;
            }
        });
        assertTrue(fields.containsAll(Set.of("a:I", "b:I", "c:I", "a:D")), fields.toString());
    }
    @Test void executionGuardShadowAndMethodExist() throws Exception {
        Set<String> members = new HashSet<>();
        read("baritone/pathing/movement/Movement", new ClassVisitor(Opcodes.ASM9) {
            @Override public FieldVisitor visitField(int access, String name, String desc, String sig, Object value) {
                if ((access & Opcodes.ACC_FINAL) != 0) members.add(name + ":" + desc); return null;
            }
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                members.add(name + desc); return null;
            }
        });
        assertTrue(members.contains("a:Lbaritone/api/IBaritone;"));
        assertTrue(members.contains("a:[Lbaritone/api/utils/BetterBlockPos;"));
        assertTrue(members.contains("c:Lbaritone/api/utils/BetterBlockPos;"));
        assertTrue(members.contains("update()Lbaritone/api/pathing/movement/MovementStatus;"));
    }
}
