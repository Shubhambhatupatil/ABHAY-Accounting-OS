package com.anvritai.abhay.config;
import io.swagger.v3.oas.models.*;import io.swagger.v3.oas.models.info.*;import io.swagger.v3.oas.models.security.*;import org.springdoc.core.customizers.OperationCustomizer;import org.springframework.context.annotation.*;
@Configuration public class OpenApiConfig{
 @Bean OpenAPI abhayOpenApi(){return new OpenAPI().info(new Info().title("ABHAY Accounting OS API").version("10.0").description("Company-scoped accounting, intelligence and automation APIs by ANVRITAI.").contact(new Contact().name("ANVRITAI"))).addSecurityItem(new SecurityRequirement().addList("bearerAuth")).components(new Components().addSecuritySchemes("bearerAuth",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));}
 @Bean OperationCustomizer controllerTags(){return(operation,handler)->{if(operation.getTags()==null||operation.getTags().isEmpty())operation.addTagsItem(handler.getBeanType().getSimpleName().replace("Controller",""));return operation;};}
}
