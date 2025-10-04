package Capstone.CSmart.global.apiPayload.exception;

import io.swagger.v3.oas.models.examples.Example;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ExampleHolder {
	private final Example holder;
	private final int code;
	private final String name;
}