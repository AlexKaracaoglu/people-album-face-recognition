import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import mu.KotlinLogging
import software.amazon.awssdk.services.rekognition.RekognitionClient

private val logger = KotlinLogging.logger {  }

class DeleteCollection(
    private val rekognitionClient: RekognitionClient = RekognitionClient.create()
): RequestHandler<String, String> {

    override fun handleRequest(collectionName: String, context: Context): String {
        logger.info { "Deleting the collection named: $collectionName" }
        val response = rekognitionClient.deleteCollection { it.collectionId(collectionName).build() }
        logger.info { "Deletion status code: ${response.statusCode()}" }
        return "Deletion of collection $collectionName - Status: ${response.statusCode()}"
    }

}