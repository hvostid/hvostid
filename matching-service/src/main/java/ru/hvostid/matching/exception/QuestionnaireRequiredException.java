package ru.hvostid.matching.exception;

/**
 * Thrown by endpoints that personalize their response based on the buyer's
 * questionnaire (e.g. recommendations) when the caller has not yet submitted
 * one. Maps to HTTP 400 because the request is missing a precondition the
 * client controls, as opposed to {@link QuestionnaireNotFoundException} which
 * maps to 404 when fetching the resource itself.
 */
public class QuestionnaireRequiredException extends RuntimeException {
    public QuestionnaireRequiredException(String message) {
        super(message);
    }
}
