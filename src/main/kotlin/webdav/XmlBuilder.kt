package me.kkywalk2.webdav

/**
 * XML builder for WebDAV responses
 */
object XmlBuilder {

    /**
     * Build a multi-status XML response for PROPFIND
     */
    fun buildMultiStatus(resources: List<WebDavResource>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<D:multistatus xmlns:D=\"DAV:\">\n")

        for (resource in resources) {
            sb.append(buildResponse(resource))
        }

        sb.append("</D:multistatus>")
        return sb.toString()
    }

    /**
     * Build a single response element
     */
    private fun buildResponse(resource: WebDavResource): String {
        return """
  <D:response>
    <D:href>${escapeXml(resource.href)}</D:href>
    <D:propstat>
      <D:prop>
        <D:displayname>${escapeXml(resource.displayName)}</D:displayname>
        <D:resourcetype>${resource.getResourceType()}</D:resourcetype>
        <D:getcontentlength>${resource.contentLength}</D:getcontentlength>
        <D:getlastmodified>${resource.formatLastModified()}</D:getlastmodified>
        <D:getetag>${resource.etag}</D:getetag>
        <D:getcontenttype>${resource.contentType}</D:getcontenttype>
      </D:prop>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>

        """.trimIndent()
    }

    /**
     * Escape XML special characters
     */
    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Build error response
     */
    fun buildError(href: String, statusCode: Int, statusMessage: String): String {
        return """
<?xml version="1.0" encoding="utf-8"?>
<D:multistatus xmlns:D="DAV:">
  <D:response>
    <D:href>${escapeXml(href)}</D:href>
    <D:status>HTTP/1.1 $statusCode $statusMessage</D:status>
  </D:response>
</D:multistatus>
        """.trimIndent()
    }
}
