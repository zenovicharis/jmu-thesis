package org.example.coom.compiler.metrics;

import java.nio.file.Files;
import java.nio.file.Path;

/** Generates synthetic COOM benchmark datasets for G1/G2/G3. */
public final class SyntheticCoomGeneratorCli {

    private SyntheticCoomGeneratorCli() {}

    public static void main(String[] args) throws Exception {
        Path outDir = Path.of("coom-transpiler/example/bench");
        int g1Depth = 3;
        int g1Branch = 2;
        int g2N = 10;
        int g2M = 8;
        int g3K = 6;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--out" -> outDir = Path.of(nextArg(args, ++i, arg));
                case "--g1-depth" -> g1Depth = parsePositiveInt(nextArg(args, ++i, arg), arg);
                case "--g1-branch" -> g1Branch = parsePositiveInt(nextArg(args, ++i, arg), arg);
                case "--g2-n" -> g2N = parsePositiveInt(nextArg(args, ++i, arg), arg);
                case "--g2-m" -> g2M = parsePositiveInt(nextArg(args, ++i, arg), arg);
                case "--g3-k" -> g3K = parsePositiveInt(nextArg(args, ++i, arg), arg);
                case "-h", "--help" -> {
                    printUsage();
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown arg: " + arg);
            }
        }

        Files.createDirectories(outDir);

        Path g1File = outDir.resolve("g1-balanced-d" + g1Depth + "-b" + g1Branch + ".coom");
        Path g2File = outDir.resolve("g2-attrfanout-n" + g2N + "-m" + g2M + ".coom");
        Path g3File = outDir.resolve("g3-defaultconflict-k" + g3K + ".coom");

        Files.writeString(g1File, generateG1(g1Depth, g1Branch));
        Files.writeString(g2File, generateG2(g2N, g2M));
        Files.writeString(g3File, generateG3(g3K));

        System.out.println("Generated:");
        System.out.println("  " + g1File);
        System.out.println("  " + g2File);
        System.out.println("  " + g3File);
    }

    private static String generateG1(int depth, int branch) {
        StringBuilder sb = new StringBuilder();

        sb.append("product {\n");
        sb.append("    1..1 G1Node0 root\n");
        sb.append("}\n\n");

        for (int level = 0; level <= depth; level++) {
            sb.append("structure G1Node").append(level).append(" {\n");
            sb.append("    num 0-100 metric_").append(level).append("\n");
            if (level < depth) {
                for (int child = 1; child <= branch; child++) {
                    sb.append("    1..1 G1Node").append(level + 1).append(" child_")
                            .append(level).append("_").append(child).append("\n");
                }
            }
            sb.append("}\n\n");
        }

        return sb.toString();
    }

    private static String generateG2(int n, int m) {
        StringBuilder sb = new StringBuilder();

        sb.append("product {\n");
        for (int i = 1; i <= n; i++) {
            sb.append("    1..1 G2Comp").append(i).append(" comp_").append(i).append("\n");
        }
        sb.append("}\n\n");

        sb.append("enumeration G2Choice {\n");
        for (int i = 1; i <= Math.max(3, Math.min(12, m)); i++) {
            sb.append("    C").append(i).append("\n");
        }
        sb.append("}\n\n");

        for (int comp = 1; comp <= n; comp++) {
            sb.append("structure G2Comp").append(comp).append(" {\n");
            for (int attr = 1; attr <= m; attr++) {
                int kind = (attr - 1) % 4;
                if (kind == 0) {
                    sb.append("    num 0-100 num_").append(attr).append("\n");
                } else if (kind == 1) {
                    sb.append("    bool flag_").append(attr).append("\n");
                } else if (kind == 2) {
                    sb.append("    string text_").append(attr).append("\n");
                } else {
                    sb.append("    G2Choice choice_").append(attr).append("\n");
                }
            }
            sb.append("}\n\n");
        }

        return sb.toString();
    }

    private static String generateG3(int k) {
        StringBuilder sb = new StringBuilder();

        sb.append("product {\n");
        sb.append("    num 0-300 speed\n");
        sb.append("    G3Mode mode\n");
        sb.append("}\n\n");

        sb.append("enumeration G3Mode {\n");
        sb.append("    Urban\n");
        sb.append("    Cargo\n");
        sb.append("    Sport\n");
        sb.append("}\n\n");

        String[] conditions = {
                "mode = Urban",
                "mode = Cargo",
                "mode = Urban",
                "mode = Sport"
        };

        for (int i = 0; i < k; i++) {
            int speed = 20 + (i * 5);
            sb.append("behavior {\n");
            sb.append("    explanation \"Conflicting default candidate ").append(i + 1).append(".\"\n");
            sb.append("    condition ").append(conditions[i % conditions.length]).append("\n");
            sb.append("    default speed = ").append(speed).append("\n");
            sb.append("}\n\n");
        }

        return sb.toString();
    }

    private static int parsePositiveInt(String raw, String arg) {
        int parsed;
        try {
            parsed = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected integer for " + arg + ": " + raw, e);
        }
        if (parsed <= 0) {
            throw new IllegalArgumentException("Expected > 0 for " + arg + ": " + raw);
        }
        return parsed;
    }

    private static String nextArg(String[] args, int i, String flag) {
        if (i >= args.length) {
            throw new IllegalArgumentException("Missing value for " + flag);
        }
        return args[i];
    }

    private static void printUsage() {
        System.out.println("Usage: SyntheticCoomGeneratorCli [options]");
        System.out.println("Options:");
        System.out.println("  --out <dir>         Output directory (default: coom-transpiler/example/bench)");
        System.out.println("  --g1-depth <int>    BalancedComposition depth d (default: 3)");
        System.out.println("  --g1-branch <int>   BalancedComposition branching b (default: 2)");
        System.out.println("  --g2-n <int>        AttributeFanout number of structures n (default: 10)");
        System.out.println("  --g2-m <int>        AttributeFanout attributes per structure m (default: 8)");
        System.out.println("  --g3-k <int>        DefaultConflict number of conditioned defaults k (default: 6)");
    }
}
