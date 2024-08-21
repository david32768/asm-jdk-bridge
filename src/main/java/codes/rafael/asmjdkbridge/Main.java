package codes.rafael.asmjdkbridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

public class Main {


    public static void main(String[] args) throws IOException, InterruptedException {
        String classfile = null;
        boolean node = false;
        switch (args.length) {
            case 1 -> classfile = args[0];
            case 2 -> {
                classfile = args[1];
                node = args[0].equals("--node");
                if (!node){
                    usage();
                }
            }
            default -> usage();
        }
        
        assert classfile != null;
        
        System.out.format("%nRoundTrip %s%n", classfile);
        try {
             boolean ok = roundtrip(classfile, node);
            String result = ok? "successful": "unsuccessful";
            System.out.format("RoundTrip of %s was %s%n", classfile, result);    
        } catch (Exception ex) {
            System.out.format("RoundTrip of %s was unsuccessful%n    %s%n", classfile, ex);
            System.err.format("%nRoundTrip %s%n", classfile);
            ex.printStackTrace();
        }
    }

    private static void usage() {
        System.err.println("Usage: [--node]? [class-file|class-name]" );
        System.err.println("    roundtrip class and compare" );
        System.err.println("    --node goes via ClassNode" );
        System.exit(1);
    }

    private static boolean roundtrip(String classfile, boolean node) throws IOException, InterruptedException {
        byte[] bytes1;
        if (classfile.endsWith(".class")) {        
                Path path = Paths.get(classfile);
                bytes1 = Files.readAllBytes(path);
        } else {
            InputStream isx = ClassLoader.getSystemResourceAsStream(classfile.replace('.', '/') + ".class");
            bytes1 = isx.readAllBytes();
        }
        ClassModel cm = ClassFile.of().parse(bytes1);
        JdkClassReader cr = new JdkClassReader(cm);
        JdkClassWriter cw = new JdkClassWriter(0);
        if (node) {
            ClassNode cn = new ClassNode();
            cr.accept(cn);
            cn.accept(cw);
        } else {
            cr.accept(cw);
        }
        byte[] bytes2 = cw.toByteArray();
        String s1 = textify(bytes1);
        String s2 = textify(bytes2);
        return compare(s1, s2);
    }
    
    private static String textify(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);
        Printer printer = new Textifier();
        StringWriter sw = new StringWriter(Short.MAX_VALUE);
        try (PrintWriter pw = new PrintWriter(sw)) {
            TraceClassVisitor tcv = new TraceClassVisitor(null, printer, pw);
            cn.accept(tcv);
        }
        return sw.toString();
    }
    
    private final static String WINDOWS_COMPARE_CMD = "fc.exe";
    
    private static boolean compare(String s1, String s2) throws IOException, InterruptedException {
        if (s1.equals(s2)) {
            return true; 
        } else {
            Path path1 = Files.createTempFile(null, null);
            Path path2 = Files.createTempFile(null, null);
            Files.writeString(path1, s1);
            Files.writeString(path2, s2);
            new ProcessBuilder(WINDOWS_COMPARE_CMD, path1.toString(), path2.toString())
                    .inheritIO()
                    .start()
                    .waitFor();
            Files.deleteIfExists(path1);
            Files.deleteIfExists(path2);
            return false; 
        }        
    }
}
