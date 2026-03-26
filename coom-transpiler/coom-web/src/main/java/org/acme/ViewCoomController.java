package org.acme;

import io.quarkus.qute.Template;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.example.coom.compiler.CompilationOptions;
import org.example.coom.compiler.CoomCompiler;
import org.example.coom.compiler.RdfFormat;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/view-coom")
public class ViewCoomController {

    @Inject
    Template viewCoom;

    private static final ConcurrentHashMap<String, ViewModel> VIEW_STATE = new ConcurrentHashMap<>();
    private static final int VIEW_STATE_MAX = 200;


    @GET
    @Produces(MediaType.TEXT_HTML)
    public String showForm(@jakarta.ws.rs.QueryParam("token") String token) {
        ViewModel model = null;
        if (token != null && !token.isBlank()) {
            model = VIEW_STATE.remove(token);
        }
        if (model == null) {
            model = ViewModel.empty();
        }
        return viewCoom.data("title", "View .coom File")
                .data("description", "Upload a .coom file to view its content and transpile it.")
                .data("fileContent", model.fileContent)
                .data("fileName", model.fileName)
                .data("useAntler", model.useAntler)
                .data("selectedFormat", model.selectedFormat)
                .data("transpileResult", model.transpileResult)
                .data("diagnostics", model.diagnostics)
                .data("message", model.message)
                .data("validationReport", model.validationReport)
                .data("conforms", model.conforms)
                .data("shapesSelection", model.shapesSelection)
                .render();
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public Response handleUpload(@RestForm("file") FileUpload fileUpload,
                                 @RestForm("format") String formatParam,
                                 @RestForm("sampleContent") String sampleContent,
                                 @RestForm("sampleName") String sampleName,
                                 @RestForm("shapes") java.util.List<String> shapesSelection,
                                 @Context HttpHeaders headers) {
        String fileContent = "";
        String transpileResult = "";
        String message = "";
        List<org.example.coom.compiler.diagnostic.Diagnostic> diagnostics = List.of();
        String validationReport = "";
        boolean conforms = true;
        String fileName = "model.coom"; // fallback
        boolean useAntler = true;
        List<String> shapesList = java.util.List.of("syntactic-core", "semantic-consistency", "profile-refinements");

        try {
            boolean hasUpload = fileUpload != null && fileUpload.filePath() != null && fileUpload.size() > 0;
            if (!hasUpload && (sampleContent == null || sampleContent.isBlank())) {
                message = "Please select a .coom file or choose a sample.";
            } else {
                byte[] bytes;
                if (hasUpload) {
                    // Read all bytes from upload
                    bytes = Files.readAllBytes(fileUpload.filePath());
                    fileContent = new String(bytes, StandardCharsets.UTF_8);
                    // Prefer filename from upload, fallback to headers
                    if (fileUpload.fileName() != null && !fileUpload.fileName().isBlank()) {
                        fileName = fileUpload.fileName();
                    } else {
                        fileName = extractFilename(headers, fileName);
                    }
                } else {
                    // Use sample content provided by the UI
                    fileContent = sampleContent;
                    bytes = sampleContent.getBytes(StandardCharsets.UTF_8);
                    if (sampleName != null && !sampleName.isBlank()) {
                        fileName = sampleName.trim();
                    }
                }

                var fmt = RdfFormat.from(formatParam);
                shapesList = (shapesSelection == null || shapesSelection.isEmpty())
                        ? java.util.List.of("syntactic-core", "semantic-consistency", "profile-refinements")
                        : shapesSelection.stream().filter(s -> s != null && !s.isBlank()).toList();
                var options = CompilationOptions.builder()
                        .format(fmt)
                        .validate(true)
                        .shapesPaths(shapesList)
                        .build();
                var compiler = new CoomCompiler();
                var result = compiler.compile(bytes, fileName, options);
                transpileResult = result.content();
                diagnostics = result.diagnostics();
                validationReport = result.validationReport();
                conforms = result.conforms();

                if (diagnostics != null && !diagnostics.isEmpty()) {
                    var firstError = diagnostics.stream()
                            .filter(d -> d.severity() == org.example.coom.compiler.diagnostic.Severity.ERROR)
                            .findFirst();
                    if (firstError.isPresent()) {
                        message = firstError.get().message();
                    }
                }
            }
        } catch (IOException e) {
            message = "Error reading file: " + e.getMessage();
        }

        String token = UUID.randomUUID().toString();
        if (VIEW_STATE.size() >= VIEW_STATE_MAX) {
            VIEW_STATE.clear();
        }
        VIEW_STATE.put(token, new ViewModel(
                fileContent,
                fileName,
                useAntler,
                formatParam == null ? "turtle" : formatParam,
                transpileResult,
                diagnostics,
                message,
                validationReport,
                conforms,
                shapesList
        ));

        return Response.seeOther(UriBuilder.fromPath("/view-coom")
                        .queryParam("token", token)
                        .build())
                .build();
    }

    @POST
    @Path("/download")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response download(@FormParam("source") String source,
                             @FormParam("fileName") String fileName,
                             @FormParam("format") String formatParam) {
        if (source == null || source.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Missing source content").build();
        }
        if (fileName == null || fileName.isBlank()) fileName = "model.coom";

        var fmt = RdfFormat.from(formatParam);
        var options = CompilationOptions.builder()
                .format(fmt)
                .validate(true)
                .shapesPath(null) // download uses last compiled result; shapes irrelevant here
                .build();
        var compiler = new CoomCompiler();
        var result = compiler.compile(source.getBytes(StandardCharsets.UTF_8), fileName, options);

        return Response.ok(result.content())
                .type(result.contentType())
                .header("Content-Disposition", "attachment; filename=\"" + result.filename() + "\"")
                .build();
    }

    private String extractFilename(HttpHeaders headers, String fallback) {
        try {
            for (var part : headers.getRequestHeaders().getOrDefault("Content-Disposition", java.util.List.of())) {
                // heuristic: look for filename="..."
                var m = java.util.regex.Pattern.compile("filename=\"([^\"]+)\"").matcher(part);
                if (m.find()) return m.group(1);
            }
        } catch (Exception ignored) { }
        return fallback;
    }

    private static final class ViewModel {
        final String fileContent;
        final String fileName;
        final boolean useAntler;
        final String selectedFormat;
        final String transpileResult;
        final List<org.example.coom.compiler.diagnostic.Diagnostic> diagnostics;
        final String message;
        final String validationReport;
        final boolean conforms;
        final List<String> shapesSelection;

        private ViewModel(String fileContent,
                          String fileName,
                          boolean useAntler,
                          String selectedFormat,
                          String transpileResult,
                          List<org.example.coom.compiler.diagnostic.Diagnostic> diagnostics,
                          String message,
                          String validationReport,
                          boolean conforms,
                          List<String> shapesSelection) {
            this.fileContent = fileContent;
            this.fileName = fileName;
            this.useAntler = useAntler;
            this.selectedFormat = selectedFormat;
            this.transpileResult = transpileResult;
            this.diagnostics = diagnostics == null ? List.of() : diagnostics;
            this.message = message == null ? "" : message;
            this.validationReport = validationReport == null ? "" : validationReport;
            this.conforms = conforms;
            this.shapesSelection = shapesSelection == null ? List.of() : shapesSelection;
        }

        static ViewModel empty() {
            return new ViewModel("", "", true, "turtle", "", List.of(), "", "", true,
                    List.of("syntactic-core", "semantic-consistency", "profile-refinements"));
        }
    }
}
