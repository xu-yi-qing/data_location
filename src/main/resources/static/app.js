// ==================== 工具函数 ====================
function get(url) {
    return fetch(url).then(function(r) { return r.json(); });
}

function setOptions(sel, items, placeholder) {
    sel.innerHTML = '<option value="">' + placeholder + '</option>';
    items.forEach(function(item) {
        var opt = document.createElement('option');
        opt.value = item.code;
        opt.textContent = item.name;
        sel.appendChild(opt);
    });
}

// ==================== 三级联动 ====================
var selProv = document.getElementById('selProv');
var selCity = document.getElementById('selCity');
var selDist = document.getElementById('selDist');

// 初始化省列表
get('/api/regions/provinces').then(function(res) {
    setOptions(selProv, res.data, '-- 选择省份 --');
});

selProv.addEventListener('change', function() {
    selCity.innerHTML = '<option value="">-- 选择城市 --</option>';
    selDist.innerHTML = '<option value="">-- 选择区县 --</option>';
    clearHistory();
    if (!selProv.value) return;

    loadHistory(selProv.value);

    get('/api/regions/cities?province=' + selProv.value).then(function(res) {
        if (res.data.length === 0) {
            // 直辖市，直接加载区县
            get('/api/regions/districts?city=' + selProv.value).then(function(r) {
                setOptions(selDist, r.data, '-- 选择区县 --');
                selCity.innerHTML = '<option value="">（直辖市）</option>';
            });
        } else {
            setOptions(selCity, res.data, '-- 选择城市 --');
        }
    });
});

selCity.addEventListener('change', function() {
    selDist.innerHTML = '<option value="">-- 选择区县 --</option>';
    if (!selCity.value) {
        if (selProv.value) loadHistory(selProv.value);
        else clearHistory();
        return;
    }

    loadHistory(selCity.value);

    get('/api/regions/districts?city=' + selCity.value).then(function(res) {
        setOptions(selDist, res.data, '-- 选择区县 --');
    });
});

selDist.addEventListener('change', function() {
    document.getElementById('streetResult').innerHTML = '';
    if (!selDist.value) {
        if (selCity.value) loadHistory(selCity.value);
        else if (selProv.value) loadHistory(selProv.value);
        else clearHistory();
        return;
    }
    loadHistory(selDist.value);
});

// ==================== 历史查询 ====================
function clearHistory() {
    document.getElementById('historyResult').innerHTML = '';
    document.getElementById('streetResult').innerHTML = '';
}

function buildHistoryHtml(d) {
    var html = '<div>' +
        '<table><caption style="text-align:left;font-weight:600;margin-bottom:6px;font-size:14px">当前行政区划</caption>' +
        '<thead><tr><th width="38%">区划代码</th><th>行政区划地址</th></tr></thead><tbody>';
    html += '<tr><td><span class="code-badge">' + d.current.code + '</span></td>' +
            '<td>' + (d.current.name || '—') + '</td></tr>';
    html += '</tbody></table></div>';

    html += '<div>' +
        '<table><caption style="text-align:left;font-weight:600;margin-bottom:6px;font-size:14px">历史行政区划</caption>' +
        '<thead><tr><th width="38%">区划代码</th><th>行政区划地址</th></tr></thead><tbody>';
    if (d.predecessors && d.predecessors.length > 0) {
        d.predecessors.forEach(function(p) {
            html += '<tr><td><span class="code-badge old">' + p.code + '</span></td>' +
                    '<td>' + (p.name || '—') + '</td></tr>';
        });
    } else {
        html += '<tr><td colspan="2" class="empty">暂无历史记录</td></tr>';
    }
    html += '</tbody></table></div>';
    return html;
}

function loadHistory(code) {
    get('/api/regions/history/' + code).then(function(res) {
        document.getElementById('historyResult').innerHTML = buildHistoryHtml(res.data);
    });
}

// ==================== 街道查询 ====================
function loadStreets() {
    var code = selDist.value;
    if (!code) { alert('请先选择区县'); return; }
    var container = document.getElementById('streetResult');
    container.innerHTML = '<p style="color:#aaa;font-size:13px;margin-top:10px">加载中…</p>';
    get('/api/regions/streets?district=' + code).then(function(res) {
        if (!res.data || res.data.length === 0) {
            container.innerHTML = '<p style="color:#aaa;font-size:13px;margin-top:10px">暂无街道数据</p>';
            return;
        }
        var html = '<p style="font-size:13px;color:#555;margin-top:14px;margin-bottom:8px">街道/镇/乡（共 ' + res.data.length + ' 个）</p>' +
                   '<div class="street-grid">';
        res.data.forEach(function(s) {
            html += '<div class="street-item"><span class="code-badge" style="margin-right:4px">' +
                    s.code + '</span>' + s.name + '</div>';
        });
        html += '</div>';
        container.innerHTML = html;
    });
}

// ==================== 模糊搜索 ====================
var searchTimer = null;

function doSearch() {
    var q = document.getElementById('searchInput').value.trim();
    var resultEl = document.getElementById('searchResult');
    if (!q) { resultEl.style.display = 'none'; return; }

    clearTimeout(searchTimer);
    searchTimer = setTimeout(function() {
        var useList2 = document.getElementById('chkList2').checked;
        var url = '/api/regions/search?q=' + encodeURIComponent(q) + '&source=' + (useList2 ? 'list2' : 'list');
        get(url).then(function(res) {
            if (!res.data || res.data.length === 0) {
                resultEl.innerHTML = '<li class="empty">无匹配结果</li>';
            } else {
                resultEl.innerHTML = res.data.map(function(item) {
                    return '<li onclick="selectSearchResult(\'' + item.code + '\')">' +
                           '<span class="code-badge">' + item.code + '</span>' +
                           '<span>' + item.fullName + '</span></li>';
                }).join('');
            }
            resultEl.style.display = 'block';
        });
    }, 250);
}

function selectSearchResult(code) {
    document.getElementById('searchResult').style.display = 'none';
    var detail = document.getElementById('searchDetail');
    detail.innerHTML = '<p style="color:#aaa;font-size:13px">加载中…</p>';
    get('/api/regions/history/' + code).then(function(res) {
        detail.innerHTML = buildHistoryHtml(res.data);
    });
}

// 点击外部关闭搜索结果
document.addEventListener('click', function(e) {
    if (!e.target.closest('#searchResult') && !e.target.closest('#searchInput')) {
        document.getElementById('searchResult').style.display = 'none';
    }
});

// ==================== 身份证解析 ====================
function parseIdCard() {
    var id = document.getElementById('idInput').value.trim();
    if (!id) { alert('请输入身份证号码'); return; }
    var resultEl = document.getElementById('idResult');
    resultEl.innerHTML = '<p style="color:#aaa;font-size:13px">解析中…</p>';

    get('/api/idcard/parse?id=' + encodeURIComponent(id)).then(function(res) {
        var d = res.data;
        var validHtml = d.valid
            ? '<span class="valid">✓ 有效</span>'
            : '<span class="invalid">✗ 无效</span>';

        var issueName = (d.issuePlace && d.issuePlace.address) ? d.issuePlace.address : '未知';
        var issueCode = d.issuePlace ? d.issuePlace.code : '';
        var isCurrent = d.issuePlace ? d.issuePlace.current : true;

        var currentPlacesHtml = '';
        if (!isCurrent && d.currentPlaces && d.currentPlaces.length > 0) {
            currentPlacesHtml = '<tr><td class="th">现所属地</td><td>' +
                d.currentPlaces.map(function(p) {
                    return '<span class="tag">' + p.code + '</span> ' + p.name;
                }).join('<br>') + '</td></tr>';
        }

        var html = '<table style="margin-top:12px">' +
            '<tr><td style="width:25%;font-weight:600;color:#555">证件号码</td>' +
            '    <td style="font-family:monospace">' + d.idNumber + '</td></tr>' +
            '<tr><td style="font-weight:600;color:#555">有效性</td><td>' + validHtml + '</td></tr>' +
            '<tr><td style="font-weight:600;color:#555">出生日期</td><td>' + (d.birthday || '—') + '</td></tr>' +
            '<tr><td style="font-weight:600;color:#555">性别</td><td>' + (d.gender || '—') + '</td></tr>' +
            '<tr><td style="font-weight:600;color:#555">首次签发地</td><td>' +
                '<span class="code-badge' + (isCurrent ? '' : ' old') + '">' + issueCode + '</span> ' +
                issueName + (isCurrent ? '' : ' <span style="color:#e67e22;font-size:12px">（已撤销）</span>') +
            '</td></tr>' +
            currentPlacesHtml +
            '</table>';

        resultEl.innerHTML = html;
    });
}

// 回车触发身份证查询
document.getElementById('idInput').addEventListener('keydown', function(e) {
    if (e.key === 'Enter') parseIdCard();
});
