package org.example.coom.compiler;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI demo utility:
 *
 * Usage:
 *   java -cp target/classes org.example.coom.compiler.CoomProfilesCli <path-to-coom> <nlin|nout|nfull> [--antlr]
 *
 * Example:
 *   java -cp coom-compiler/target/classes org.example.coom.compiler.CoomProfilesCli ComfortBike.coom nlin
 */
public final class CoomProfilesCli {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: CoomProfilesCli <file.coom> <nlin|nout|nfull> [--antlr]");
            System.exit(2);
        }

        Path p = Path.of(args[0]);
        String profile = args[1];
        boolean useAntlr = args.length >= 3 && "--antlr".equalsIgnoreCase(args[2]);

        byte[] bytes = Files.readAllBytes(p);

        var api = new CoomProfiles();
        var res = api.generate(bytes, p.getFileName().toString(), profile, useAntlr);

        System.out.print(res.content());
    }
}
