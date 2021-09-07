import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.S3Event
import mu.KotlinLogging
import software.amazon.awssdk.services.rekognition.RekognitionClient
import software.amazon.awssdk.services.rekognition.model.Image
import software.amazon.awssdk.services.rekognition.model.S3Object
import software.amazon.awssdk.services.s3.S3Client
import java.util.*

private val logger = KotlinLogging.logger {  }

class IndexFacesAndOutput(
    private val rekognitionClient: RekognitionClient = RekognitionClient.create(),
    private val s3Client: S3Client = S3Client.create(),
    private val collectionName: String = System.getenv("collectionName")
): RequestHandler<S3Event, Unit> {

    override fun handleRequest(s3Event: S3Event, context: Context) {
        val bucketName = s3Event.records[0].s3.bucket.name
        val croppedFaceImageKey = s3Event.records[0].s3.`object`.key
        val inputFullImageKey = s3Client.getObjectTagging { it.bucket(bucketName).key(croppedFaceImageKey).build() }.tagSet()
            .associate { it.key() to it.value() }["originalImageKey"]!!

        logger.info { "Original Input Image Key: $inputFullImageKey" }
        logger.info { "Cropped Face Image Key: $croppedFaceImageKey"}

        val s3Object = S3Object.builder().bucket(bucketName).name(croppedFaceImageKey).build()
        val image = Image.builder().s3Object(s3Object).build()
        logger.info { "Searching the collection for a face matching the cropped face image with key: $croppedFaceImageKey" }
        val searchResponse = rekognitionClient.searchFacesByImage {
            it.image(image).collectionId(collectionName)
        }

        logger.info { "Number of face matches: ${searchResponse.faceMatches().size}" }
        if (searchResponse.faceMatches().size == 0) {
            logger.info { "No match found - creating new person id and indexing the face" }
            val newPersonId = UUID.randomUUID().toString()
            logger.info { "New person id: $newPersonId" }
            val indexResponse = rekognitionClient.indexFaces { it.image(image).collectionId(collectionName).maxFaces(1).externalImageId(newPersonId) }
            val faceId = indexResponse.faceRecords()[0].face().faceId()
            copyInputImageAndCroppedImageToOutputLocation(
                bucketName,
                inputFullImageKey,
                croppedFaceImageKey,
                newPersonId,
                faceId
            )
        }
        else {
            logger.info { "A match was found - doing analysis" }
            val matchedPersonId = searchResponse.faceMatches()[0].face().externalImageId()
            logger.info { "Face was matched to person id: $matchedPersonId" }
            val indexResponse = rekognitionClient.indexFaces { it.image(image).collectionId(collectionName).maxFaces(1).externalImageId(matchedPersonId) }
            val faceId = indexResponse.faceRecords()[0].face().faceId()
            copyInputImageAndCroppedImageToOutputLocation(
                bucketName,
                inputFullImageKey,
                croppedFaceImageKey,
                matchedPersonId,
                faceId
            )
        }
    }

    private fun copyInputImageAndCroppedImageToOutputLocation(bucketName: String, inputImageKey: String, croppedImageKey: String, personId: String, faceId: String) {
        logger.info { "Face was indexed to personId: $personId and faceId: $faceId" }
        val fullImageOutputLocation = "output/$personId/full-images/${inputImageKey.split("/").last()}"
        val indexedImageOutputLocation = "output/$personId/indexed-images/$faceId.png"
        logger.info { "Copying object from: $bucketName/$inputImageKey -> $fullImageOutputLocation" }
        s3Client.copyObject { it.copySource("$bucketName/$inputImageKey").destinationBucket(bucketName).destinationKey(fullImageOutputLocation) }
        logger.info { "Copying object from: $bucketName/$croppedImageKey -> $indexedImageOutputLocation" }
        s3Client.copyObject { it.copySource("$bucketName/$croppedImageKey").destinationBucket(bucketName).destinationKey(indexedImageOutputLocation) }
    }

}