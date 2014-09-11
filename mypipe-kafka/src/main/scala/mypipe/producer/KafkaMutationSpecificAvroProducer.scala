package mypipe.producer

import mypipe.api._
import com.typesafe.config.Config
import mypipe.avro.schema.{ AvroSchemaUtils, GenericSchemaRepository }
import mypipe.avro.{ AvroVersionedRecordSerializer }
import mypipe.kafka.KafkaUtil
import org.apache.avro.Schema
import org.apache.avro.generic.{ GenericData }

class KafkaMutationSpecificAvroProducer(config: Config)
    extends KafkaMutationAvroProducer[Short](config) {

  private val schemaRepoClientClassName = config.getString("schema-repo-client")

  override protected val serializer = new AvroVersionedRecordSerializer[InputRecord](schemaRepoClient)
  override protected val schemaRepoClient = Class.forName(schemaRepoClientClassName)
    .newInstance()
    .asInstanceOf[GenericSchemaRepository[Short, Schema]]

  override protected def schemaIdToByteArray(s: Short) = Array[Byte](((s & 0xFF00) >> 8).toByte, (s & 0x00FF).toByte)

  override protected def getKafkaTopic(mutation: Mutation[_]): String = KafkaUtil.specificTopic(mutation)

  override protected def avroRecord(mutation: Mutation[_], schema: Schema): GenericData.Record = {

    Mutation.getMagicByte(mutation) match {
      case Mutation.InsertByte ⇒ insertMutationToAvro(mutation.asInstanceOf[InsertMutation], schema)
      case Mutation.UpdateByte ⇒ updateMutationToAvro(mutation.asInstanceOf[UpdateMutation], schema)
      case Mutation.DeleteByte ⇒ deleteMutationToAvro(mutation.asInstanceOf[DeleteMutation], schema)
      case _ ⇒ {
        logger.error(s"Unexpected mutation type ${mutation.getClass} encountered; retuning empty Avro GenericData.Record(schema=$schema")
        new GenericData.Record(schema)
      }
    }
  }

  /** Given a mutation, returns the "subject" that this mutation's
   *  Schema is registered under in the Avro schema repository.
   *
   *  @param mutation
   *  @return returns "mutationDbName_mutationTableName_mutationType" where mutationType is "insert", "update", or "delete"
   */
  override protected def avroSchemaSubject(mutation: Mutation[_]): String = AvroSchemaUtils.specificSubject(mutation)

  protected def insertMutationToAvro(mutation: InsertMutation, schema: Schema): GenericData.Record = {

    val record = new GenericData.Record(schema)

    mutation.rows.head.columns.foreach(col ⇒ {

      Option(schema.getField(col._1)).map(f ⇒ record.put(f.name(), col._2.value))

    })

    record
  }

  protected def updateMutationToAvro(mutation: UpdateMutation, schema: Schema): GenericData.Record = ???
  protected def deleteMutationToAvro(mutation: DeleteMutation, schema: Schema): GenericData.Record = ???
}