package dev.resqnet.backend.web;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

/**
 * Serialises a live event frame.
 *
 * <p>Derived from the application's configured mapper rather than built from
 * scratch, so it keeps the {@code JavaTimeModule} and every other module the
 * REST endpoints rely on. A hand-built mapper failed to serialise an
 * {@code Instant} and quietly emitted {@code serialisation-failed} for every
 * incident event, which is precisely the kind of failure this project exists to
 * avoid: the dashboard looked fine and simply never heard about anything.
 *
 * <p>Inclusion is then overridden to always write nulls. The API omits them,
 * which suits a REST response, but an event frame that loses a field whenever
 * it happens to be null leaves the client unable to apply the change it was
 * told about.
 */
@Component
public class EventJson {

    private final ObjectMapper mapper;

    public EventJson(ObjectMapper applicationMapper) {
        this.mapper = applicationMapper.copy()
                .setSerializationInclusion(JsonInclude.Include.ALWAYS);
    }

    public String encode(Map<String, Object> body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            // An event that cannot be serialised must not take down the commit
            // that produced it. The dashboard still polls, so the worst outcome
            // is a missed live nudge rather than a lost incident.
            return "{\"type\":\"serialisation-failed\"}";
        }
    }
}