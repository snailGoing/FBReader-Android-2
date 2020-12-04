<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
    <xsl:output indent="yes" method="xml" />
    <xsl:strip-space elements="*" />
    <xsl:template match="comment()" />
    <xsl:template match="node">
        <xsl:if test="@name != ''">
            <node name="{@name}">
                <xsl:apply-templates />
            </node>
        </xsl:if>
    </xsl:template>
    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" />
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>
