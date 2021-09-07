import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import mu.KotlinLogging
import software.amazon.awssdk.services.rekognition.RekognitionClient
import kotlin.collections.List as List

private val logger = KotlinLogging.logger {  }

class ListFacesInCollection(
    private val rekognitionClient: RekognitionClient = RekognitionClient.create(),
    private val collectionName: String = System.getenv("collectionName")
): RequestHandler<Unit, List<FaceDC>> {

    override fun handleRequest(any: Unit, context: Context): List<FaceDC> {
        logger.info { "Getting all faces" }
        val listResponse = rekognitionClient.listFaces { it.collectionId(collectionName) }.faces()
        logger.info { "There are ${listResponse.size} faces in collection: $collectionName" }
        return listResponse.map {
            FaceDC(faceId = it.faceId(), personId = it.externalImageId())
        }
    }

}

data class FaceDC(
    val faceId: String,
    val personId: String
)