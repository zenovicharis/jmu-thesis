package org.example.coom.compiler;

import java.util.List;
import java.util.ArrayList;
import org.example.coom.compiler.profile.CoomProfile;

public final class CompilationOptions {
    private final RdfFormat format;
    private final List<CoomProfile> profiles;
    private final boolean traceStages;
    private final String productNameOverride;
    private final boolean validate;
    private final List<String> shapesPaths;

    private CompilationOptions(Builder builder) {
        this.format = builder.format;
        this.profiles = List.copyOf(builder.profiles);
        this.traceStages = builder.traceStages;
        this.productNameOverride = builder.productNameOverride;
        this.validate = builder.validate;
        this.shapesPaths = List.copyOf(builder.shapesPaths);
    }

    public RdfFormat format() {
        return format;
    }

    public List<CoomProfile> profiles() {
        return profiles;
    }

    public boolean traceStages() {
        return traceStages;
    }

    public String productNameOverride() {
        return productNameOverride;
    }

    public boolean validate() {
        return validate;
    }

    public String shapesPath() {
        return shapesPaths.isEmpty() ? null : shapesPaths.get(0);
    }

    public List<String> shapesPaths() {
        return shapesPaths;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private RdfFormat format = RdfFormat.TURTLE;
        private List<CoomProfile> profiles = CoomProfile.defaultPipeline();
        private boolean traceStages;
        private String productNameOverride;
        private boolean validate;
        private List<String> shapesPaths = new ArrayList<>();

        public Builder format(RdfFormat format) {
            if (format != null) this.format = format;
            return this;
        }

        public Builder profiles(List<CoomProfile> profiles) {
            if (profiles != null && !profiles.isEmpty()) {
                this.profiles = profiles;
            }
            return this;
        }

        public Builder traceStages(boolean traceStages) {
            this.traceStages = traceStages;
            return this;
        }

        public Builder productNameOverride(String productNameOverride) {
            this.productNameOverride = productNameOverride;
            return this;
        }

        public Builder validate(boolean validate) {
            this.validate = validate;
            return this;
        }

        public Builder shapesPath(String shapesPath) {
            this.shapesPaths = new ArrayList<>();
            if (shapesPath != null && !shapesPath.isBlank()) {
                this.shapesPaths.add(shapesPath);
            }
            return this;
        }

        public Builder shapesPaths(List<String> shapesPaths) {
            this.shapesPaths = new ArrayList<>();
            if (shapesPaths != null) {
                for (String s : shapesPaths) {
                    if (s != null && !s.isBlank()) {
                        this.shapesPaths.add(s);
                    }
                }
            }
            return this;
        }

        public CompilationOptions build() {
            return new CompilationOptions(this);
        }
    }
}
