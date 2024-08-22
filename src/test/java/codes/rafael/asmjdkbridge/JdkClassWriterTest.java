package codes.rafael.asmjdkbridge;

import codes.rafael.asmjdkbridge.sample.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.classfile.CustomAttribute;
import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class JdkClassWriterTest {

    @SuppressWarnings("deprecation")
    @Parameterized.Parameters(name = "{0} (reader={1}, writer={2})")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {Trivial.class, 0, 0},
                {LoadStoreAndReturn.class, 0, 0},
                {FieldConstructorAndMethod.class, 0, 0},
                {Operations.class, 0, 0},
                {DeprecatedClass.class, 0, 0},
                {SyntheticConstructor.Inner.class, 0, 0},
                {ArrayInstructions.class, 0, 0},
                {Invokedynamic.class, 0, 0},
                {BranchesAndStackMapFrames.class, 0, ClassWriter.COMPUTE_FRAMES},
                {BranchesAndStackMapFrames.class, ClassReader.EXPAND_FRAMES, ClassWriter.COMPUTE_FRAMES},
                {Switches.class, 0, ClassWriter.COMPUTE_FRAMES},
                {TryThrowCatch.class, 0, ClassWriter.COMPUTE_FRAMES},
                {RecordComponents.class, 0, 0},
                {NoRecordComponents.class, 0, 0},
                {Annotations.class, 0, 0},
                {TypeAnnotationsWithoutPath.class, 0, 0},
                {TypeAnnotationsWithPath.class, 0, 0},
                {TypeAnnotationsInCode.class, 0, ClassWriter.COMPUTE_FRAMES},
                {CustomAttributeExtractable.make(), 0, 0},
                {SyntheticParameters.class, 0, 0},
                {SyntheticParameters.InnerClass.class, 0, 0},
                {String.class, ClassReader.SKIP_FRAMES, 0},
                {Integer.class, ClassReader.SKIP_FRAMES, 0},
                {Math.class, 0, ClassWriter.COMPUTE_FRAMES}
        });
    }

    private final Class<?> target;
    private final int readerFlags, writerFlags;

    public JdkClassWriterTest(Class<?> target, int readerFlags, int writerFlags) {
        this.target = target;
        this.readerFlags = readerFlags;
        this.writerFlags = writerFlags;
    }

    @Test
    public void parsed_class_files_are_equal() throws IOException {
        byte[] classFile;
        try (InputStream inputStream = target.getResourceAsStream(target.getName().substring(target.getPackageName().length() + 1) + ".class")) {
            classFile = inputStream.readAllBytes();
        }
        StringWriter asm = new StringWriter(), jdk = new StringWriter();
        toClassReader(classFile).accept(toVisitor(asm), readerFlags);
        JdkClassWriter writer = new JdkClassWriter(writerFlags, attribute -> {
            if (attribute instanceof TestAttribute testAttribute) {
                return testAttribute.attribute;
            } else {
                throw new AssertionError("Unknown attribute: " + attribute.type);
            }
        });
        toClassReader(classFile).accept(writer, new Attribute[]{ new TestAttribute() }, readerFlags);
        toClassReader(writer.toByteArray()).accept(toVisitor(jdk), readerFlags);
        assertEquals(asm.toString(), jdk.toString());
    }

    private static ClassVisitor toVisitor(StringWriter writer) {
        return new TraceClassVisitor(new PrintWriter(writer));
    }

    private static ClassReader toClassReader(byte[] bytes) {
        try {
            Constructor<ClassReader> constructor = ClassReader.class.getDeclaredConstructor(byte[].class, int.class, boolean.class);
            constructor.setAccessible(true);
            return constructor.newInstance(bytes, 0, false);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static class TestAttribute extends Attribute {

        private CustomAttribute<?> attribute;

        protected TestAttribute() {
            super("CustomAttribute");
        }

        @Override
        @SuppressWarnings("deprecation")
        protected Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeAttributeOffset, Label[] labels) {
            TestAttribute attribute = new TestAttribute();
            attribute.attribute = new byte[length];
            System.arraycopy(classReader.b, offset, attribute.attribute, 0, length);
            return attribute;
        }

        @Override
        protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
            ByteVector vector = new ByteVector(attribute.length);
            vector.putByteArray(attribute, 0, attribute.length);
            return vector;
        }
    }
}
