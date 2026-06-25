package com.signasource.signa_api.notification.config;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.signasource.signa_api.notification.entity.NotificationCode;
import com.signasource.signa_api.notification.entity.NotificationScope;
import com.signasource.signa_api.notification.entity.NotificationTemplate;
import com.signasource.signa_api.notification.repository.NotificationTemplateRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationCatalogSeeder implements CommandLineRunner {

	private final NotificationTemplateRepository templateRepository;

	@Override
	public void run(String... args) {
		List<NotificationTemplate> defaults = List.of(
				template(NotificationCode.DAILY_REMINDER, NotificationScope.INDIVIDUAL, true, "Es hora de practicar",
						"No pierdas el ritmo, dedica unos minutos a aprender hoy."),
				template(NotificationCode.DAILY_REMINDER, NotificationScope.INDIVIDUAL, true,
						"Tu practica diaria te espera", "Unos minutos al dia hacen la diferencia. Vamos a practicar!"),
				template(NotificationCode.DAILY_REMINDER, NotificationScope.INDIVIDUAL, true,
						"No te olvides de practicar", "La constancia es clave. Dedica un momento a repasar hoy."),

				template(NotificationCode.COURSE_COMPLETED, NotificationScope.INDIVIDUAL, false, "Curso completado!",
						"Felicitaciones por completar {{course}}."),

				template(NotificationCode.STREAK_REMINDER, NotificationScope.INDIVIDUAL, true, "Mantene tu racha!",
						"Llevas {{streak}} dias seguidos. Segui asi!"),
				template(NotificationCode.STREAK_REMINDER, NotificationScope.INDIVIDUAL, true,
						"{{streak}} dias y contando!", "Tu dedicacion esta dando frutos. No pares ahora!"),

				template(NotificationCode.NEW_COURSE_AVAILABLE, NotificationScope.INDIVIDUAL, false,
						"Nuevo curso disponible", "Ya podes empezar {{course}}."),

				template(NotificationCode.GLOBAL_ANNOUNCEMENT, NotificationScope.GLOBAL, false, "Novedades",
						"Tenemos novedades para vos."));

		Map<NotificationCode, List<NotificationTemplate>> byCode = defaults.stream()
				.collect(Collectors.groupingBy(NotificationTemplate::getCode));

		for (Map.Entry<NotificationCode, List<NotificationTemplate>> entry : byCode.entrySet()) {
			if (!templateRepository.existsByCode(entry.getKey())) {
				templateRepository.saveAll(entry.getValue());
			}
		}
	}

	private NotificationTemplate template(NotificationCode code, NotificationScope scope, boolean schedulable,
			String title, String body) {
		return NotificationTemplate.builder().code(code).scope(scope).schedulable(schedulable).enabled(true)
				.defaultTitle(title).defaultBody(body).build();
	}
}
