package org.example.coom.compiler.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.example.coom.compiler.CompilationOptions;
import org.example.coom.compiler.CompilationResult;
import org.example.coom.compiler.CoomCompiler;
import org.example.coom.compiler.RdfFormat;
import org.example.coom.compiler.profile.BaseProfile;
import org.example.coom.compiler.profile.CProfile;
import org.example.coom.compiler.profile.CoomProfile;
import org.example.coom.compiler.profile.NProfileStage;
import org.example.coom.compiler.NProfileGenerator;

public final class CoomCli {
    public static void main(String[] args) {
        if (args.length == 0 || hasHelp(args)) {
            printUsage();
            System.exit(1);
        }

        String input = args[0];
        String out = null;
        String formatParam = null;
        String profilesParam = null;
        boolean trace = false;
        String productName = null;
        boolean validate = false;
        String shapesPath = null;

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-o", "--out" -> out = nextArg(args, ++i, arg);
                case "-f", "--format" -> formatParam = nextArg(args, ++i, arg);
                case "--profiles" -> profilesParam = nextArg(args, ++i, arg);
                case "--trace" -> trace = true;
                case "--name" -> productName = nextArg(args, ++i, arg);
                case "--validate" -> validate = true;
                case "--shapes" -> shapesPath = nextArg(args, ++i, arg);
                default -> {
                    System.err.println("Unknown arg: " + arg);
                    printUsage();
                    System.exit(2);
                }
            }
        }

        try {
            byte[] bytes = Files.readAllBytes(Path.of(input));
            String fileName = Path.of(input).getFileName().toString();

            List<CoomProfile> profiles = parseProfiles(profilesParam);

            CompilationOptions options = CompilationOptions.builder()
                    .format(RdfFormat.from(formatParam))
                    .profiles(profiles)
                    .traceStages(trace)
                    .productNameOverride(productName)
                    .validate(validate)
                    .shapesPath(shapesPath)
                    .build();

            CompilationResult result = new CoomCompiler().compile(bytes, fileName, options);

            if (result.diagnostics() != null) {
                result.diagnostics().forEach(d -> System.err.println(d.severity() + " [" + d.stage() + "] " + d.message()));
            }

            if (!result.conforms()) {
                System.err.println("SHACL validation: NON-CONFORMANT");
                if (result.validationReport() != null) {
                    System.err.println(result.validationReport());
                }
                System.exit(4);
            }

            if (result.content() == null || result.content().isEmpty()) {
                System.err.println("No output produced.");
                System.exit(3);
            }

            if (out != null) {
                Files.writeString(Path.of(out), result.content());
                System.out.println("Wrote: " + out);
            } else {
                System.out.println(result.content());
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(3);
        }
    }

    private static boolean hasHelp(String[] args) {
        return Arrays.stream(args).anyMatch(a -> "-h".equals(a) || "--help".equals(a));
    }

    private static void printUsage() {
        System.out.println("Usage: coom-compiler <file.coom> [options]");
        System.out.println("Options:");
        System.out.println("  -o, --out <path>         Output file (default: stdout)");
        System.out.println("  -f, --format <fmt>       turtle|rdfxml|ntriples|jsonld");
        System.out.println("  --profiles <list>        comma-separated: base,n,c (default: base,n,c)");
        System.out.println("  --trace                  Trace pipeline stages");
        System.out.println("  --name <productName>     Override product name");
        System.out.println("  --validate               Run SHACL validation after RDF build");
        System.out.println("  --shapes <path>          Shapes file for SHACL (Turtle/RDF formats)");
    }

    private static String nextArg(String[] args, int i, String flag) {
        if (i >= args.length) {
            System.err.println("Missing value for " + flag);
            System.exit(2);
        }
        return args[i];
    }

    private static List<CoomProfile> parseProfiles(String param) {
        if (param == null || param.isBlank()) {
            return CoomProfile.defaultPipeline();
        }

        List<CoomProfile> profiles = new ArrayList<>();
        for (String token : param.split(",")) {
            String t = token.trim().toLowerCase();
            switch (t) {
                case "base" -> profiles.add(new BaseProfile());
                case "n" -> profiles.add(new NProfileStage(NProfileGenerator.NProfile.N_LIN));
                case "c" -> profiles.add(new CProfile());
                default -> {
                    System.err.println("Unknown profile: " + token);
                    System.exit(2);
                }
            }
        }
        return profiles;
    }
}
