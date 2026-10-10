package app.parsing;

import java.util.List;

public record ParsedResponse(String message, List<String> successors) {}