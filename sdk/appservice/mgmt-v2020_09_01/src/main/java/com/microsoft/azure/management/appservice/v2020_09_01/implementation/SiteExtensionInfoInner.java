/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 *
 * Code generated by Microsoft (R) AutoRest Code Generator.
 */

package com.microsoft.azure.management.appservice.v2020_09_01.implementation;

import com.microsoft.azure.management.appservice.v2020_09_01.SiteExtensionType;
import java.util.List;
import org.joda.time.DateTime;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.microsoft.rest.serializer.JsonFlatten;
import com.microsoft.azure.management.appservice.v2020_09_01.ProxyOnlyResource;

/**
 * Site Extension Information.
 */
@JsonFlatten
public class SiteExtensionInfoInner extends ProxyOnlyResource {
    /**
     * Site extension ID.
     */
    @JsonProperty(value = "properties.extension_id")
    private String extensionId;

    /**
     * The title property.
     */
    @JsonProperty(value = "properties.title")
    private String title;

    /**
     * Site extension type. Possible values include: 'Gallery', 'WebRoot'.
     */
    @JsonProperty(value = "properties.extension_type")
    private SiteExtensionType extensionType;

    /**
     * Summary description.
     */
    @JsonProperty(value = "properties.summary")
    private String summary;

    /**
     * Detailed description.
     */
    @JsonProperty(value = "properties.description")
    private String description;

    /**
     * Version information.
     */
    @JsonProperty(value = "properties.version")
    private String version;

    /**
     * Extension URL.
     */
    @JsonProperty(value = "properties.extension_url")
    private String extensionUrl;

    /**
     * Project URL.
     */
    @JsonProperty(value = "properties.project_url")
    private String projectUrl;

    /**
     * Icon URL.
     */
    @JsonProperty(value = "properties.icon_url")
    private String iconUrl;

    /**
     * License URL.
     */
    @JsonProperty(value = "properties.license_url")
    private String licenseUrl;

    /**
     * Feed URL.
     */
    @JsonProperty(value = "properties.feed_url")
    private String feedUrl;

    /**
     * List of authors.
     */
    @JsonProperty(value = "properties.authors")
    private List<String> authors;

    /**
     * Installer command line parameters.
     */
    @JsonProperty(value = "properties.installer_command_line_params")
    private String installerCommandLineParams;

    /**
     * Published timestamp.
     */
    @JsonProperty(value = "properties.published_date_time")
    private DateTime publishedDateTime;

    /**
     * Count of downloads.
     */
    @JsonProperty(value = "properties.download_count")
    private Integer downloadCount;

    /**
     * &lt;code&gt;true&lt;/code&gt; if the local version is the latest
     * version; &lt;code&gt;false&lt;/code&gt; otherwise.
     */
    @JsonProperty(value = "properties.local_is_latest_version")
    private Boolean localIsLatestVersion;

    /**
     * Local path.
     */
    @JsonProperty(value = "properties.local_path")
    private String localPath;

    /**
     * Installed timestamp.
     */
    @JsonProperty(value = "properties.installed_date_time")
    private DateTime installedDateTime;

    /**
     * Provisioning state.
     */
    @JsonProperty(value = "properties.provisioningState")
    private String provisioningState;

    /**
     * Site Extension comment.
     */
    @JsonProperty(value = "properties.comment")
    private String comment;

    /**
     * Get site extension ID.
     *
     * @return the extensionId value
     */
    public String extensionId() {
        return this.extensionId;
    }

    /**
     * Set site extension ID.
     *
     * @param extensionId the extensionId value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withExtensionId(String extensionId) {
        this.extensionId = extensionId;
        return this;
    }

    /**
     * Get the title value.
     *
     * @return the title value
     */
    public String title() {
        return this.title;
    }

    /**
     * Set the title value.
     *
     * @param title the title value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withTitle(String title) {
        this.title = title;
        return this;
    }

    /**
     * Get site extension type. Possible values include: 'Gallery', 'WebRoot'.
     *
     * @return the extensionType value
     */
    public SiteExtensionType extensionType() {
        return this.extensionType;
    }

    /**
     * Set site extension type. Possible values include: 'Gallery', 'WebRoot'.
     *
     * @param extensionType the extensionType value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withExtensionType(SiteExtensionType extensionType) {
        this.extensionType = extensionType;
        return this;
    }

    /**
     * Get summary description.
     *
     * @return the summary value
     */
    public String summary() {
        return this.summary;
    }

    /**
     * Set summary description.
     *
     * @param summary the summary value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withSummary(String summary) {
        this.summary = summary;
        return this;
    }

    /**
     * Get detailed description.
     *
     * @return the description value
     */
    public String description() {
        return this.description;
    }

    /**
     * Set detailed description.
     *
     * @param description the description value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Get version information.
     *
     * @return the version value
     */
    public String version() {
        return this.version;
    }

    /**
     * Set version information.
     *
     * @param version the version value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withVersion(String version) {
        this.version = version;
        return this;
    }

    /**
     * Get extension URL.
     *
     * @return the extensionUrl value
     */
    public String extensionUrl() {
        return this.extensionUrl;
    }

    /**
     * Set extension URL.
     *
     * @param extensionUrl the extensionUrl value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withExtensionUrl(String extensionUrl) {
        this.extensionUrl = extensionUrl;
        return this;
    }

    /**
     * Get project URL.
     *
     * @return the projectUrl value
     */
    public String projectUrl() {
        return this.projectUrl;
    }

    /**
     * Set project URL.
     *
     * @param projectUrl the projectUrl value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withProjectUrl(String projectUrl) {
        this.projectUrl = projectUrl;
        return this;
    }

    /**
     * Get icon URL.
     *
     * @return the iconUrl value
     */
    public String iconUrl() {
        return this.iconUrl;
    }

    /**
     * Set icon URL.
     *
     * @param iconUrl the iconUrl value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
        return this;
    }

    /**
     * Get license URL.
     *
     * @return the licenseUrl value
     */
    public String licenseUrl() {
        return this.licenseUrl;
    }

    /**
     * Set license URL.
     *
     * @param licenseUrl the licenseUrl value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withLicenseUrl(String licenseUrl) {
        this.licenseUrl = licenseUrl;
        return this;
    }

    /**
     * Get feed URL.
     *
     * @return the feedUrl value
     */
    public String feedUrl() {
        return this.feedUrl;
    }

    /**
     * Set feed URL.
     *
     * @param feedUrl the feedUrl value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withFeedUrl(String feedUrl) {
        this.feedUrl = feedUrl;
        return this;
    }

    /**
     * Get list of authors.
     *
     * @return the authors value
     */
    public List<String> authors() {
        return this.authors;
    }

    /**
     * Set list of authors.
     *
     * @param authors the authors value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withAuthors(List<String> authors) {
        this.authors = authors;
        return this;
    }

    /**
     * Get installer command line parameters.
     *
     * @return the installerCommandLineParams value
     */
    public String installerCommandLineParams() {
        return this.installerCommandLineParams;
    }

    /**
     * Set installer command line parameters.
     *
     * @param installerCommandLineParams the installerCommandLineParams value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withInstallerCommandLineParams(String installerCommandLineParams) {
        this.installerCommandLineParams = installerCommandLineParams;
        return this;
    }

    /**
     * Get published timestamp.
     *
     * @return the publishedDateTime value
     */
    public DateTime publishedDateTime() {
        return this.publishedDateTime;
    }

    /**
     * Set published timestamp.
     *
     * @param publishedDateTime the publishedDateTime value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withPublishedDateTime(DateTime publishedDateTime) {
        this.publishedDateTime = publishedDateTime;
        return this;
    }

    /**
     * Get count of downloads.
     *
     * @return the downloadCount value
     */
    public Integer downloadCount() {
        return this.downloadCount;
    }

    /**
     * Set count of downloads.
     *
     * @param downloadCount the downloadCount value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withDownloadCount(Integer downloadCount) {
        this.downloadCount = downloadCount;
        return this;
    }

    /**
     * Get &lt;code&gt;true&lt;/code&gt; if the local version is the latest version; &lt;code&gt;false&lt;/code&gt; otherwise.
     *
     * @return the localIsLatestVersion value
     */
    public Boolean localIsLatestVersion() {
        return this.localIsLatestVersion;
    }

    /**
     * Set &lt;code&gt;true&lt;/code&gt; if the local version is the latest version; &lt;code&gt;false&lt;/code&gt; otherwise.
     *
     * @param localIsLatestVersion the localIsLatestVersion value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withLocalIsLatestVersion(Boolean localIsLatestVersion) {
        this.localIsLatestVersion = localIsLatestVersion;
        return this;
    }

    /**
     * Get local path.
     *
     * @return the localPath value
     */
    public String localPath() {
        return this.localPath;
    }

    /**
     * Set local path.
     *
     * @param localPath the localPath value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withLocalPath(String localPath) {
        this.localPath = localPath;
        return this;
    }

    /**
     * Get installed timestamp.
     *
     * @return the installedDateTime value
     */
    public DateTime installedDateTime() {
        return this.installedDateTime;
    }

    /**
     * Set installed timestamp.
     *
     * @param installedDateTime the installedDateTime value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withInstalledDateTime(DateTime installedDateTime) {
        this.installedDateTime = installedDateTime;
        return this;
    }

    /**
     * Get provisioning state.
     *
     * @return the provisioningState value
     */
    public String provisioningState() {
        return this.provisioningState;
    }

    /**
     * Set provisioning state.
     *
     * @param provisioningState the provisioningState value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withProvisioningState(String provisioningState) {
        this.provisioningState = provisioningState;
        return this;
    }

    /**
     * Get site Extension comment.
     *
     * @return the comment value
     */
    public String comment() {
        return this.comment;
    }

    /**
     * Set site Extension comment.
     *
     * @param comment the comment value to set
     * @return the SiteExtensionInfoInner object itself.
     */
    public SiteExtensionInfoInner withComment(String comment) {
        this.comment = comment;
        return this;
    }

}
