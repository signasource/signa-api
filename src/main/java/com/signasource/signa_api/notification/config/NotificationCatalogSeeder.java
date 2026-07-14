package com.signasource.signa_api.notification.config;

import com.signasource.signa_api.notification.entity.NotificationCode;
import com.signasource.signa_api.notification.entity.NotificationScope;
import com.signasource.signa_api.notification.entity.NotificationTemplate;
import com.signasource.signa_api.notification.repository.NotificationTemplateRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationCatalogSeeder implements CommandLineRunner {

    private final NotificationTemplateRepository templateRepository;

    @Override
    public void run(String... args) {
        List<NotificationTemplate> defaults =
                List.of(
                        template(
                                NotificationCode.DAILY_REMINDER,
                                NotificationScope.INDIVIDUAL,
                                true,
                                "Es hora de practicar",
                                "No pierdas el ritmo, dedicá unos minutos a aprender hoy."),
                        template(
                                NotificationCode.DAILY_REMINDER,
                                NotificationScope.INDIVIDUAL,
                                true,
                                "Tu práctica diaria te espera",
                                "Unos minutos al día hacen la diferencia. ¡Vamos a practicar!"),
                        template(
                                NotificationCode.DAILY_REMINDER,
                                NotificationScope.INDIVIDUAL,
                                true,
                                "No te olvides de practicar",
                                "La constancia es clave. Dedica un momento a repasar hoy."),
                        template(
                                NotificationCode.COURSE_COMPLETED,
                                NotificationScope.INDIVIDUAL,
                                false,
                                "Curso completado!",
                                "Felicitaciones por completar {{course}}."),
                        template(
                                NotificationCode.STREAK_REMINDER,
                                NotificationScope.INDIVIDUAL,
                                true,
                                "¡Mantené tu racha!",
                                "Llevás {{streak}} días seguidos. ¡Seguí así!"),
                        template(
                                NotificationCode.STREAK_REMINDER,
                                NotificationScope.INDIVIDUAL,
                                true,
                                "¡{{streak}} días y contando!",
                                "Tu dedicación esta dando frutos. ¡No parés ahora!"),
                        template(
                                NotificationCode.NEW_COURSE_AVAILABLE,
                                NotificationScope.INDIVIDUAL,
                                false,
                                "Nuevo curso disponible",
                                "Ya podés empezar {{course}}."),
                        template(
                                NotificationCode.GLOBAL_ANNOUNCEMENT,
                                NotificationScope.GLOBAL,
                                false,
                                "Novedades",
                                "Tenemos novedades para vos."));

        for (NotificationTemplate t : defaults) {
            if (!templateRepository.existsByCodeAndDefaultTitle(t.getCode(), t.getDefaultTitle())) {
                templateRepository.save(t);
            }
        }
    }

    private NotificationTemplate template(
            NotificationCode code,
            NotificationScope scope,
            boolean schedulable,
            String title,
            String body) {
        return NotificationTemplate.builder()
                .code(code)
                .scope(scope)
                .schedulable(schedulable)
                .enabled(true)
                .defaultTitle(title)
                .defaultBody(body)
                .build();
    }
}
