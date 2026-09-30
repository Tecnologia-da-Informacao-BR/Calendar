package br.com.calendar.openapi;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("Calendar API")
                        .version("1.0.0")
                        .description("API documentation"))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .name("bearerAuth")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .in(SecurityScheme.In.HEADER)));


        Paths paths = new Paths();


        paths.addPathItem("/auth/signup", new PathItem().post(createOp("Register user", "201", "Created")));
        paths.addPathItem("/auth/login", new PathItem().post(createOp("Authenticate user", "200", "OK")));
        paths.addPathItem("/auth/forgot-password", new PathItem().post(createOp("Request password reset", "200", "OK")));
        paths.addPathItem("/auth/verify-otp", new PathItem().post(createOp("Verify password reset OTP", "200", "OK")));
        paths.addPathItem("/auth/logout", new PathItem().post(createOp("Logout user", "204", "No Content")));
        paths.addPathItem("/auth/reset-password", new PathItem().post(createOp("Reset password using OTP", "200", "OK")));
        paths.addPathItem("/auth/confirm-email", new PathItem().post(createOp("Confirm email using the token from the confirmation link", "200", "OK")));


        paths.addPathItem("/users/me", new PathItem()
                .get(createOp("Get authenticated user", "200", "OK"))
                .patch(createOp("Update user", "200", "OK")));
        paths.addPathItem("/users/me/password", new PathItem().patch(createOp("Change user password", "204", "No Content")));


        paths.addPathItem("/categories", new PathItem()
                .post(createOp("Create a category for the authenticated user", "201", "Created"))
                .get(createOp("Get the authenticated user's categories", "200", "OK")));

        Operation deleteCategory = createOp("Delete a category", "204", "No Content")
                .addParametersItem(createPathParam("id", "string"))
                .responses(new ApiResponses()
                        .addApiResponse("204", new ApiResponse().description("No Content"))
                        .addApiResponse("409", new ApiResponse().description("Cannot delete category with associated tasks")));

        paths.addPathItem("/categories/{id}", new PathItem()
                .patch(createOp("Update a category", "200", "OK").addParametersItem(createPathParam("id", "string")))
                .delete(deleteCategory));


        Operation getTasks = createOp("Get tasks", "200", "OK")
                .addParametersItem(createQueryParams("date", "string", "date"))
                .addParametersItem(createQueryParams("month", "integer", null))
                .addParametersItem(createQueryParams("year", "integer", null));

        paths.addPathItem("/tasks", new PathItem()
                .post(createOp("Create a task", "201", "Created"))
                .get(getTasks));

        paths.addPathItem("/tasks/history", new PathItem().get(createOp("Get task history", "200", "OK")));


        paths.addPathItem("/tasks/{id}", new PathItem()
                .put(createOp("Replace task", "200", "OK").addParametersItem(createPathParam("id", "string")))
                .patch(createOp("Update task", "200", "OK").addParametersItem(createPathParam("id", "string")))
                .delete(createOp("Delete task", "204", "No Content").addParametersItem(createPathParam("id", "string"))));

        openAPI.setPaths(paths);
        return openAPI;
    }


    private Operation createOp(String summary, String statusCode, String description) {
        return new Operation()
                .summary(summary)
                .responses(new ApiResponses().addApiResponse(statusCode, new ApiResponse().description(description)));
    }

    private Parameter createQueryParams(String name, String type, String format) {
        return new Parameter()
                .name(name)
                .in("query")
                .schema(new Schema<>().type(type).format(format));
    }

    private Parameter createPathParam(String name, String type) {
        return new Parameter()
                .name(name)
                .in("path")
                .required(true)
                .schema(new Schema<>().type(type));
    }

}
