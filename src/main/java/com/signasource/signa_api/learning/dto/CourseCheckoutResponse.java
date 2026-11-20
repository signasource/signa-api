package com.signasource.signa_api.learning.dto;

import java.util.UUID;

/** Where to send the buyer to pay (Mercado Pago Checkout Pro) and the order to check afterwards. */
public record CourseCheckoutResponse(UUID orderId, String checkoutUrl) {}
