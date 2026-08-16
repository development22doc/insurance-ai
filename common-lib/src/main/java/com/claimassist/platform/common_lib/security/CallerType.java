package com.claimassist.platform.common_lib.security;

/**
 * Explicitly distinguishes the two kinds of bearer token the platform accepts,
 * so shared security code (authorization, internal endpoint guards, Feign
 * propagation) never has to guess whether a token carries an end-user identity.
 *
 * <ul>
 *   <li>{@link #USER} — an end-user JWT issued through the password/authorization
 *       grant. It carries the numeric {@code userId} claim (the DB-owned business
 *       id the platform keys every foreign key on) plus {@code preferred_username}.</li>
 *   <li>{@link #SERVICE} — a machine-to-machine client-credentials JWT issued to a
 *       registered service account. It does NOT carry an end-user {@code userId};
 *       its identity is the OAuth2 client id (the {@code azp} / {@code client_id}
 *       claim). Internal endpoints that act on a specific user's behalf must
 *       therefore obtain that user identity from a propagated end-user token or an
 *       explicit parameter, never by assuming a service token carries a userId.</li>
 * </ul>
 */
public enum CallerType {
    USER,
    SERVICE
}