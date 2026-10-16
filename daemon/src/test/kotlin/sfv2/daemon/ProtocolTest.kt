package sfv2.daemon

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProtocolTest {

    @Test
    fun `valid ping is parsed`() {
        assertThat(parseRequest("""{"cmd":"ping"}""")).isEqualTo(Request.Ping)
    }

    @Test
    fun `invalid json is rejected without echoing the input`() {
        val request = parseRequest("secret-not-json")

        assertThat(request).isEqualTo(Request.Rejected("invalid JSON"))
    }

    @Test
    fun `non object json is rejected`() {
        assertThat(parseRequest("[1,2]")).isEqualTo(Request.Rejected("message must be a JSON object"))
    }

    @Test
    fun `missing cmd is rejected`() {
        assertThat(parseRequest("""{"foo":1}""")).isEqualTo(Request.Rejected("missing cmd"))
    }

    @Test
    fun `non text cmd is rejected`() {
        assertThat(parseRequest("""{"cmd":5}""")).isEqualTo(Request.Rejected("missing cmd"))
    }

    @Test
    fun `unknown cmd is rejected`() {
        assertThat(parseRequest("""{"cmd":"explode"}""")).isEqualTo(Request.Rejected("unknown cmd"))
    }

    @Test
    fun `pong renders as a single json line`() {
        assertThat(render(Response.Pong)).isEqualTo("""{"ok":true,"result":"pong"}""")
    }

    @Test
    fun `error message with quotes is escaped`() {
        val rendered = render(Response.Error("bad \"thing\""))

        assertThat(rendered).isEqualTo("""{"ok":false,"error":"bad \"thing\""}""")
    }
}
