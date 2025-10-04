package Capstone.CSmart.global.config;


import Capstone.CSmart.global.apiPayload.code.ErrorReasonDTO;
import Capstone.CSmart.global.apiPayload.code.status.ErrorStatus;
import Capstone.CSmart.global.apiPayload.exception.ExampleHolder;
import Capstone.CSmart.global.web.dto.util.ApiErrorCodeExample;
import Capstone.CSmart.global.web.dto.util.ApiErrorCodeExamples;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
public class SwaggerConfig {
	@Bean
	public OpenAPI CSmartAPI() {
		Info info = new Info()
			.title("CSmart Swagger")
			.description("Capstone Design Project Server Swagger")
			.version("1.0.0");

		String jwtSchemeName = "JWT TOKEN";
		SecurityRequirement securityRequirement = new SecurityRequirement().addList(jwtSchemeName);
		Components components = new Components()
			.addSecuritySchemes("JWT TOKEN", new SecurityScheme()
				.name(jwtSchemeName)
				.type(SecurityScheme.Type.HTTP) // HTTP 방식
				.in(SecurityScheme.In.HEADER)
				.scheme("bearer")
				.bearerFormat("JWT")
			);

		return new OpenAPI()
			.addServersItem(new Server().url("/"))
			.info(info)
			.addSecurityItem(securityRequirement)
			.components(components);
	}


	@Bean
	public OperationCustomizer customizeErrorResponses() {
		return (operation, handlerMethod) -> {
			var multiple = handlerMethod.getMethodAnnotation(ApiErrorCodeExamples.class);
			var single = handlerMethod.getMethodAnnotation(ApiErrorCodeExample.class);

			if (multiple != null) {
				addExamplesToResponses(operation, multiple.value());
			} else if (single != null) {
				addExamplesToResponses(operation, new ErrorStatus[]{single.value()});
			}

			return operation;
		};
	}
	private void addExamplesToResponses(Operation operation, ErrorStatus[] errorStatuses) {
		ApiResponses responses = operation.getResponses();

		Map<Integer, List<ExampleHolder>> grouped = Arrays.stream(errorStatuses)
			.map(error -> ExampleHolder.builder()
				.holder(getSwaggerExample(error))
				.code(error.getHttpStatus().value())
				.name(error.name())
				.build())
			.collect(Collectors.groupingBy(ExampleHolder::getCode));

		grouped.forEach((statusCode, exampleList) -> {
			Content content = new Content();
			MediaType mediaType = new MediaType();
			ApiResponse apiResponse = new ApiResponse();

			exampleList.forEach(holder -> mediaType.addExamples(holder.getName(), holder.getHolder()));
			content.addMediaType("application/json", mediaType);
			apiResponse.setContent(content);
			responses.addApiResponse(String.valueOf(statusCode), apiResponse);
		});
	}

	private Example getSwaggerExample(ErrorStatus errorStatus) {
		ErrorReasonDTO example = errorStatus.getReasonHttpStatus();
		Example swaggerExample = new Example();
		swaggerExample.setValue(example);
		return swaggerExample;
	}
}
