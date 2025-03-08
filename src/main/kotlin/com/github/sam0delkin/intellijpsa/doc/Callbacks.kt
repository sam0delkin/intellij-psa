@file:Suppress("ktlint:standard:max-line-length")

package com.github.sam0delkin.intellijpsa.doc

import com.github.sam0delkin.intellijpsa.model.GenerateFileFromTemplateData
import com.github.sam0delkin.intellijpsa.model.InfoModel
import com.github.sam0delkin.intellijpsa.model.PsiElementModel
import com.github.sam0delkin.intellijpsa.model.TemplateDataModel
import com.github.sam0delkin.intellijpsa.util.BuildConfig
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.info.Contact
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.info.License
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.QueryParam

@OpenAPIDefinition(
    info =
        Info(
            title = "PSA",
            description = "Project Specific Autocomplete documentation",
            version = BuildConfig.PLUGIN_VERSION,
            license = License(name = "MIT", url = "https://github.com/sam0delkin/intellij-psa?tab=MIT-1-ov-file#readme"),
            contact = Contact(url = "https://github.com/sam0delkin/intellij-psa", name = "sam0delkin", email = "me@s-l.dev"),
        ),
)
@Path("/methods")
class Callbacks {
    @Operation(
        tags = ["Methods"],
        description =
            "Get Info from your PSA script. For more info, see:" +
                "<a href=\"https://github.com/sam0delkin/intellij-psa?tab=readme-ov-file#custom-autocomplete-info\">" +
                "documentation" +
                "</a>",
        operationId = "info",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Info Model",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = InfoModel::class))],
            ),
        ],
    )
    @POST
    @Path("info")
    fun info(
        @Parameter(
            description = "PSA_TYPE",
            schema = Schema(types = ["string"], allowableValues = ["Info"]),
        )
        @QueryParam("PSA_TYPE") psaType: String,
        @Parameter(
            description = "PSA_DEBUG",
            schema = Schema(types = ["string"], allowableValues = ["1", "0"]),
        )
        @QueryParam("PSA_DEBUG") psaDebug: String,
    ): InfoModel? = null

    @Operation(
        tags = ["Methods"],
        description =
            "Get Completions for the given PSI element. For more info, see " +
                "<a href=\"https://github.com/sam0delkin/intellij-psa?tab=readme-ov-file#completions--goto\">" +
                "documentation" +
                "</a>. Note that all params are passed via ENV variables",
        operationId = "getCompletions",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Info Model",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = InfoModel::class))],
            ),
        ],
    )
    @POST
    @Path("getCompletions")
    fun getCompletions(
        @Parameter(
            name = "PSA_TYPE",
            description = "Type of the request",
            schema =
                Schema(
                    types = ["string"],
                    allowableValues = [
                        "Completion",
                        "BatchCompletion",
                        "GoTo",
                        "BatchGoTo",
                    ],
                ),
        )
        @QueryParam("PSA_TYPE") psaType: String,
        @Parameter(
            name = "PSA_DEBUG",
            description = "Is debug enabled or not",
            schema = Schema(types = ["string"], allowableValues = ["1", "0"]),
        )
        @QueryParam("PSA_DEBUG") psaDebug: String,
        @Parameter(
            name = "PSA_LANGUAGE",
            schema = Schema(types = ["string"]),
        )
        @QueryParam("PSA_LANGUAGE") psaLanguage: String,
        @Parameter(
            name = "PSA_OFFSET",
            schema = Schema(types = ["string"]),
        )
        @QueryParam("PSA_OFFSET") psaOffset: String,
        @RequestBody(
            description = "Note that body will be passed as file path to the JSON content",
            content = [
                Content(mediaType = "application/json", schema = Schema(implementation = PsiElementModel::class)),
            ],
        ) psaContext: String,
    ): InfoModel? = null

    @Operation(
        tags = ["Methods"],
        description =
            "Generate code for file template. For more info, see " +
                "<a href=\"https://github.com/sam0delkin/intellij-psa/tree/main?tab=readme-ov-file#code-templates\">" +
                "documentation" +
                "</a>. Note that all params are passed via ENV variables",
        operationId = "GenerateFileFromTemplate",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Template Data Model",
                content = [Content(mediaType = "application/json", schema = Schema(implementation = TemplateDataModel::class))],
            ),
        ],
    )
    @POST
    @Path("GenerateFileFromTemplate")
    fun generateFileFromTemplate(
        @Parameter(
            description = "PSA_TYPE",
            schema = Schema(types = ["string"], allowableValues = ["GenerateFileFromTemplate"]),
        )
        @QueryParam("PSA_TYPE") psaType: String,
        @Parameter(
            description = "PSA_DEBUG",
            schema = Schema(types = ["string"], allowableValues = ["1", "0"]),
        )
        @QueryParam("PSA_DEBUG") psaDebug: String,
        @RequestBody(
            description = "Note that body will be passed as file path to the JSON content",
            content = [
                Content(mediaType = "application/json", schema = Schema(implementation = GenerateFileFromTemplateData::class)),
            ],
        ) psaContext: String,
    ): InfoModel? = null
}
