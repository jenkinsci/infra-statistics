
function parseData(versionData, name) {
    const pluginVersionsSet = new Set();

    const coreVersionsSet = new Set();

    const totalInstallsPerPluginVersion = new Map();

    let totalInstalls = 0

    for (const pluginVersion in versionData) {
        if (/^\d[\w.]*\w$/.test(pluginVersion)) {
            pluginVersionsSet.add(pluginVersion);
            if (!totalInstallsPerPluginVersion.has(pluginVersion)) {
                totalInstallsPerPluginVersion[pluginVersion] = 0;
            }
            for (const coreVersion in versionData[pluginVersion]) {
                if (/^[12][.]\d+(|[.]\d)$/.test(coreVersion)) {
                    coreVersionsSet.add(coreVersion);
                    const install= versionData[pluginVersion][coreVersion]
                    totalInstalls += install;
                    totalInstallsPerPluginVersion[pluginVersion] += install;
                }
            }
        }
    }

    const collator = new Intl.Collator(undefined, {numeric: true, sensitivity: 'base'});

    const sortAndConvert = (parameterSet) =>
      Array.from(parameterSet).sort(collator.compare);

    const pluginVersions = sortAndConvert(pluginVersionsSet);

    const coreVersions = sortAndConvert(coreVersionsSet);  

    const thisCoreVersionOrOlderPerPluginVersion = {};

    // header row
    var row = $("<tr>");
    row.append($("<th>").html(name));
    for (const pluginVersion of pluginVersions) {
        row.append($("<th>").html(pluginVersion));
        thisCoreVersionOrOlderPerPluginVersion[pluginVersion] = 0;
    }
    row.append($("<th>").html("Sum"));
    let thead = $("<thead>");
    thead.appendTo('#versionsContainer');
    row.appendTo(thead);


    let thisCoreVersionOrOlder = 0;

    // value rows
    for (const coreVersion of coreVersions) {
        let row = $("<tr>");
        if (/^\d[.]\d+[.]\d$/.test(coreVersion)) {
            row.addClass('lts');
        }
        row.append($("<th>").html(coreVersion));
        let thisCoreVersion = 0;
        for (let pluginVersion of pluginVersions) {
            let cnt = versionData[pluginVersion][coreVersion];
            if (cnt == null) {
                cnt = 0;
            }
            thisCoreVersion += cnt;
            let title = pluginVersion + " on " + coreVersion + ": " + (cnt > 0 ? cnt + " installs (" + Math.round(cnt/totalInstalls*100) + "%) - " : "");
            title += Math.round((1-thisCoreVersionOrOlderPerPluginVersion[pluginVersion]/totalInstallsPerPluginVersion[pluginVersion])*100) + "% of " + pluginVersion + " installs are on this core version or newer";
            row.append($("<td>").attr("title", title).css("opacity", Math.max(0.1, cnt*100/totalInstalls)).html(cnt > 0 ? cnt: ""));

            thisCoreVersionOrOlderPerPluginVersion[pluginVersion] += cnt;
        }

        let title = coreVersion + " total: " + thisCoreVersion + " installs (" + Math.round(thisCoreVersion/totalInstalls*100) + "%)";
        title += " - " + Math.round((1-thisCoreVersionOrOlder/totalInstalls)*100) + "% of plugin installs are on this core version or newer";
        row.append($("<td>").addClass("subtotal").attr("title", title).html(thisCoreVersion));

        thisCoreVersionOrOlder += thisCoreVersion;

        row.appendTo('#versionsContainer');
    }

    // footer row
    let row = $("<tr>");
    row.append($("<th>").html("Total"));
    for (const pluginVersion of pluginVersions) {
        row.append($("<td>").html(totalInstallsPerPluginVersion[pluginVersion]));
    }
    row.append($("<td>").html(totalInstalls));
    let tfoot = $("<tfoot>");
    tfoot.appendTo('#versionsContainer');
    row.appendTo(tfoot);

}
