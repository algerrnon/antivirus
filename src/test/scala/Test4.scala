import java.io.File

import com.altuera.gms_antivirus_service.tpapi.{Antivirus, EmulationResultData, VerdictValues}

object Test4 {

  def main(args: Array[String]): Unit = {
    val client = new Antivirus()
    val file: File = new File("/home/algernon/av/Selection_136.png")
    println(client.upload(file))
    val emulation: Option[EmulationResultData] = client.threadEmulationQueryRetry(file)
    if (emulation.isDefined) {
      println(s"Virus=${!emulation.get.combined_verdict.equalsIgnoreCase(VerdictValues.BENIGN)}")
    }
    else {
      println("не удалось вовремя получить ответ")
    }
  }
}
