package com.signasource.signa_api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MercadoPagoProperties.class)
public class MercadoPagoConfig {}
