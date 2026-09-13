package com.signasource.signa_api.content.validator.block;

import com.signasource.signa_api.content.dto.config.PerformSignConfig;
import com.signasource.signa_api.content.dto.validation.ValidationContext;
import com.signasource.signa_api.content.dto.validation.ValidationError;
import com.signasource.signa_api.content.dto.yaml.LessonBlockDto;
import com.signasource.signa_api.content.util.BlockConfigParser;
import com.signasource.signa_api.learning.entity.BlockType;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PerformSignValidator implements BlockValidator {

    /**
     * Señas que el modelo sabe clasificar. Fuente: {@code signa-ml/models/exports/
     * signa_model_v3_meta.json}. Hay que actualizarla al cambiar el modelo. "reposo" queda afuera:
     * es la clase de "no estoy haciendo nada", no una seña.
     */
    private static final Set<String> RECOGNIZABLE_SIGNS =
            Set.of(
                    "gracias",
                    "hermanos",
                    "casa",
                    "nombre",
                    "estudiar",
                    "entender",
                    "repetir",
                    "gato",
                    "papa",
                    "mama",
                    "computadora",
                    "lengua_de_senas");

    private final BlockConfigParser parser;

    public PerformSignValidator(BlockConfigParser parser) {
        this.parser = parser;
    }

    @Override
    public BlockType supports() {
        return BlockType.PERFORM_SIGN;
    }

    @Override
    public void validate(
            LessonBlockDto block, ValidationContext ctx, List<ValidationError> errors) {
        Optional<PerformSignConfig> parsed = parser.parse(block.config(), PerformSignConfig.class);
        if (parsed.isEmpty()) {
            errors.add(
                    new ValidationError(ctx.location(), "invalid config for PERFORM_SIGN block"));
            return;
        }
        PerformSignConfig config = parsed.get();

        if (config.signs() == null || config.signs().isEmpty()) {
            errors.add(new ValidationError(ctx.location(), "signs must have at least 1 element"));
        } else {
            for (String sign : config.signs()) {
                if (sign == null || sign.isBlank()) {
                    errors.add(
                            new ValidationError(ctx.location(), "signs entries cannot be blank"));
                    continue;
                }
                String normalized = sign.strip().toLowerCase(Locale.ROOT);
                if (!RECOGNIZABLE_SIGNS.contains(normalized)) {
                    errors.add(
                            new ValidationError(
                                    ctx.location(),
                                    "the recognition model does not know the sign '"
                                            + normalized
                                            + "' — known signs: "
                                            + RECOGNIZABLE_SIGNS.stream().sorted().toList()));
                }
            }
        }

        Double threshold = config.threshold();
        if (threshold != null && (threshold <= 0 || threshold > 1)) {
            errors.add(new ValidationError(ctx.location(), "threshold must be between 0 and 1"));
        }
    }
}
