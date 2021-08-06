
function parseData(versionData, name) {
    var pluginVersionsSet = new Set();

    var coreVersionsSet = new Set();

    var totalInstallsPerPluginVersion = new Map();

    var totalInstalls = 0

    for (var pluginVersion in versionData) {
        if (/^\d[\w.]*\w$/.test(pluginVersion)) {
            pluginVersionsSet.add(pluginVersion);
            if (!totalInstallsPerPluginVersion.has(pluginVersion)) {
                totalInstallsPerPluginVersion[pluginVersion] = 0;
            }
            for (var coreVersion in versionData[pluginVersion]) {
                if (/^[12][.]\d+(|[.]\d)$/.test(coreVersion)) {
                    coreVersionsSet.add(coreVersion);
                    totalInstalls += versionData[pluginVersion][coreVersion];
                    totalInstallsPerPluginVersion[pluginVersion] += versionData[pluginVersion][coreVersion];
                }
            }
        }
    }

    var collator = new Intl.Collator(undefined, {numeric: true, sensitivity: 'base'});

    var pluginVersions = Array.from(pluginVersionsSet);
    pluginVersions.sort(collator.compare);

    var coreVersions = Array.from(coreVersionsSet);
    coreVersions.sort(collator.compare);

    var thisCoreVersionOrOlderPerPluginVersion = {};

    // header row
    var row = $("<tr>");
    row.append($("<th>").html(name));
    for (let pluginVersion of pluginVersions) {
        row.append($("<th>").html(pluginVersion));
        thisCoreVersionOrOlderPerPluginVersion[pluginVersion] = 0;
    }
    row.append($("<th>").html("Sum"));
    var thead = $("<thead>");
    thead.appendTo('#versionsContainer');
    row.appendTo(thead);


    var thisCoreVersionOrOlder = 0;

    // value rows
    for (let coreVersion of coreVersions) {
        var row = $("<tr>");
        if (/^\d[.]\d+[.]\d$/.test(coreVersion)) {
            row.addClass('lts');
        }
        row.append($("<th>").html(coreVersion));
        var thisCoreVersion = 0;
        for (let pluginVersion of pluginVersions) {
            var cnt = versionData[pluginVersion][coreVersion];
            if (cnt == null) {
                cnt = 0;
            }
            thisCoreVersion += cnt;
            var title = pluginVersion + " on " + coreVersion + ": " + (cnt > 0 ? cnt + " installs (" + Math.round(cnt/totalInstalls*100) + "%) - " : "");
            title += Math.round((1-thisCoreVersionOrOlderPerPluginVersion[pluginVersion]/totalInstallsPerPluginVersion[pluginVersion])*100) + "% of " + pluginVersion + " installs are on this core version or newer";
            row.append($("<td>").attr("title", title).css("opacity", Math.max(0.1, cnt*100/totalInstalls)).html(cnt > 0 ? cnt: ""));

            thisCoreVersionOrOlderPerPluginVersion[pluginVersion] += cnt;
        }

        var title = coreVersion + " total: " + thisCoreVersion + " installs (" + Math.round(thisCoreVersion/totalInstalls*100) + "%)";
        title += " - " + Math.round((1-thisCoreVersionOrOlder/totalInstalls)*100) + "% of plugin installs are on this core version or newer";
        row.append($("<td>").addClass("subtotal").attr("title", title).html(thisCoreVersion));

        thisCoreVersionOrOlder += thisCoreVersion;

        row.appendTo('#versionsContainer');
    }

    // footer row
    var row = $("<tr>");
    row.append($("<th>").html("Total"));
    for (let pluginVersion of pluginVersions) {
        row.append($("<td>").html(totalInstallsPerPluginVersion[pluginVersion]));
    }
    row.append($("<td>").html(totalInstalls));
    var tfoot = $("<tfoot>");
    tfoot.appendTo('#versionsContainer');
    row.appendTo(tfoot);

}
