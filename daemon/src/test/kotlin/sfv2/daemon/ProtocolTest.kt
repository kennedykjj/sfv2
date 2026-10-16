package sfv2.daemon

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import sfv2.core.Actor
import sfv2.core.Column

class ProtocolTest {

    @Test
    fun `valid ping is parsed`() {
        assertThat(parseRequest("""{"cmd":"ping"}""")).isEqualTo(Request.Ping)
    }

    @Test
    fun `card commands parse actors and columns and reject bad ones`() {
        assertThat(parseRequest("""{"cmd":"card.create","actor":"factory:gate","title":"T"}"""))
            .isEqualTo(Request.CardCreate(Actor.Factory("gate"), "T"))
        assertThat(parseRequest("""{"cmd":"card.move","actor":"agent:dev","id":"x","to":"CODING"}"""))
            .isEqualTo(Request.CardMove(Actor.Agent("dev"), "x", Column.CODING))
        assertThat(parseRequest("""{"cmd":"card.move","actor":"owner","id":"x","to":"NOWHERE"}"""))
            .isEqualTo(Request.Rejected("unknown column"))
        assertThat(parseRequest("""{"cmd":"card.create","actor":"root","title":"T"}"""))
            .isEqualTo(Request.Rejected("missing or invalid actor"))
        assertThat(parseRequest("""{"cmd":"card.create","title":"T"}"""))
            .isEqualTo(Request.Rejected("missing or invalid actor"))
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
