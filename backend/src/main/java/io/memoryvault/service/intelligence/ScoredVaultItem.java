package io.memoryvault.service.intelligence;

import io.memoryvault.domain.VaultItem;

/**
 * One vault item ranked by {@link ContextDetector} or {@link ResurfaceEngine}, paired with
 * the numeric score that produced its position and a short human-readable reason to show
 * the user why it was surfaced.
 *
 * @param item   the vault item being scored
 * @param score  the computed rank score (higher ranks first); scale and meaning depend on
 *               which engine produced it — see {@link ContextDetector#rank} and
 *               {@link ResurfaceEngine#topResurfaceCandidates}
 * @param reason a short, user-facing explanation of why this item was surfaced
 */
public record ScoredVaultItem(VaultItem item, double score, String reason) {
}
