import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import mu.KotlinLogging
import software.amazon.awssdk.services.rekognition.RekognitionClient
import kotlin.collections.List as List

private val logger = KotlinLogging.logger {  }

class ListCollections(
    private val rekognitionClient: RekognitionClient = RekognitionClient.create()
): RequestHandler<Unit, List<String>> {

    override fun handleRequest(any: Unit, context: Context): List<String> {
        logger.info { "Getting all collections" }
        val listResponse = rekognitionClient.listCollections().collectionIds()
        logger.info { "There are ${listResponse.size} collections" }
        logger.info { "Collection names: $listResponse" }
        return listResponse
    }

}