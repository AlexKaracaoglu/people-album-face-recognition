import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import mu.KotlinLogging
import software.amazon.awssdk.services.rekognition.RekognitionClient

private val logger = KotlinLogging.logger {  }

class CreateCollection(
    private val rekognitionClient: RekognitionClient = RekognitionClient.create(),
    private val collectionName: String = System.getenv("collectionName")
): RequestHandler<Unit, String> {

    override fun handleRequest(any: Unit, context: Context): String {
        logger.info { "Creating a new collection named: $collectionName" }
        val creationResponse = rekognitionClient.createCollection { it.collectionId(collectionName) }
        return "Creation response: ${creationResponse.statusCode()}"
    }

}