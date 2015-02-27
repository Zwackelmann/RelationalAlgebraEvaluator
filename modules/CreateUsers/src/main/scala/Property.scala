import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.util.{
    Properties => JavaProperties
}

object Property {
    val properties = new JavaProperties()
    val stream = new BufferedInputStream(new FileInputStream("project/application.conf"))
    properties.load(stream)
    stream.close()

    def apply(key: String) = {
        properties.getProperty(key)
    }
}