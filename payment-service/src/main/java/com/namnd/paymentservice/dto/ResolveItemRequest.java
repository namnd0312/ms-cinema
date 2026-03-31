package com.namnd.paymentservice.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for resolving a reconciliation item with optional notes.
 */
public record ResolveItemRequest(@Size(max = 1000) String notes) {}
