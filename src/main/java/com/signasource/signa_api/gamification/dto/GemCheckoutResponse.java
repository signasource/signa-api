package com.signasource.signa_api.gamification.dto;

import java.util.UUID;

/** Where to send the buyer to pay (Mercado Pago Checkout Pro) and the order to check afterwards. */
public record GemCheckoutResponse(UUID orderId, String checkoutUrl) {}
