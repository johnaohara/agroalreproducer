package reproducer;

import java.io.InputStream;

public interface URLResponse {

    String asString();

    InputStream asInputStream();

}
