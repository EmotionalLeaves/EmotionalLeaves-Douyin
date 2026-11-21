"""Probe Chinese platforms from a US runner: what a visitor (no account) can get. Writes out/<name>.*"""
import hashlib, json, os, re, sys, time, urllib.parse
import requests

OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
MUA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
s = requests.Session(); s.headers["User-Agent"] = UA

def save(name, text):
    with open(os.path.join(OUT, name), "w", encoding="utf-8") as f: f.write(text if isinstance(text, str) else json.dumps(text, ensure_ascii=False, indent=1))

def report(*a): print(*a, flush=True)

def get(url, **kw):
    try:
        r = s.get(url, timeout=20, **kw); return r
    except Exception as e:
        report("  ERR", url[:90], e); return None

# ---------------- Bilibili ----------------
MIXIN = [46,47,18,2,53,8,23,32,15,50,10,31,58,3,45,35,27,43,5,49,33,9,42,19,29,28,14,39,12,38,41,13,37,48,7,16,24,55,40,61,26,17,0,1,60,51,30,4,22,25,54,21,56,59,6,63,57,62,11,36,20,34,44,52]
def wbi_keys():
    r = get("https://api.bilibili.com/x/web-interface/nav", headers={"Referer": "https://www.bilibili.com/"})
    j = r.json(); w = j["data"]["wbi_img"]
    img = w["img_url"].rsplit("/", 1)[1].split(".")[0]; sub = w["sub_url"].rsplit("/", 1)[1].split(".")[0]
    raw = img + sub
    return "".join(raw[i] for i in MIXIN)[:32]
def wbi(params, key):
    p = dict(params); p["wts"] = int(time.time())
    p = {k: re.sub(r"[!'()*]", "", str(v)) for k, v in sorted(p.items())}
    q = urllib.parse.urlencode(p)
    p["w_rid"] = hashlib.md5((q + key).encode()).hexdigest()
    return p

def bili():
    report("== bilibili")
    hdr = {"Referer": "https://www.bilibili.com/", "Origin": "https://www.bilibili.com"}
    r = get("https://api.bilibili.com/x/frontend/finger/spi"); 
    if r is not None:
        report("  spi", r.status_code, r.text[:120])
        try:
            d = r.json()["data"]; s.cookies.set("buvid3", d["b_3"], domain=".bilibili.com"); s.cookies.set("buvid4", d["b_4"], domain=".bilibili.com")
        except Exception as e: report("  spi parse", e)
    r = get("https://www.bilibili.com/", headers=hdr); report("  home", r.status_code if r is not None else None, len(r.text) if r is not None else 0)
    try: key = wbi_keys(); report("  wbi key ok")
    except Exception as e: report("  wbi key ERR", e); key = None
    r = get("https://api.bilibili.com/x/web-interface/popular?ps=20&pn=1", headers=hdr)
    if r is not None:
        save("bili_popular.json", r.text); j = r.json(); lst = (j.get("data") or {}).get("list") or []
        report("  popular", r.status_code, j.get("code"), len(lst), [ (v.get("title") or "")[:20] for v in lst[:3]])
    first = lst[0] if r is not None and lst else None
    if key:
        r = get("https://api.bilibili.com/x/web-interface/wbi/index/top/feed/rcmd?" + urllib.parse.urlencode(wbi({"fresh_type": 4, "ps": 12, "fresh_idx": 1, "fresh_idx_1h": 1, "brush": 1, "feed_version": "V8", "homepage_ver": 1}, key)), headers=hdr)
        if r is not None:
            save("bili_rcmd.json", r.text); j = r.json(); items = (j.get("data") or {}).get("item") or []
            report("  rcmd", r.status_code, j.get("code"), len(items), [(i.get("title") or "")[:20] for i in items[:3]])
        r = get("https://api.bilibili.com/x/web-interface/wbi/search/type?" + urllib.parse.urlencode(wbi({"search_type": "video", "keyword": "美食", "page": 1}, key)), headers=hdr)
        if r is not None:
            save("bili_search.json", r.text); j = r.json(); res = (j.get("data") or {}).get("result") or []
            report("  search", r.status_code, j.get("code"), j.get("message"), len(res), [re.sub("<[^>]+>", "", i.get("title") or "")[:20] for i in res[:3]])
        r = get("https://api.bilibili.com/x/web-interface/ranking/v2?rid=0&type=all", headers=hdr)
        if r is not None:
            j = r.json(); report("  ranking", r.status_code, j.get("code"), len(((j.get("data") or {}).get("list") or [])))
    if first:
        bvid, cid = first.get("bvid"), first.get("cid")
        for plat in ("html5", "pc"):
            params = {"bvid": bvid, "cid": cid, "qn": 64, "fnval": 1 if plat == "html5" else 4048, "fnver": 0, "fourk": 1, "platform": plat, "high_quality": 1}
            url = "https://api.bilibili.com/x/player/wbi/playurl?" + urllib.parse.urlencode(wbi(params, key) if key else params)
            r = get(url, headers=hdr)
            if r is None: continue
            save(f"bili_playurl_{plat}.json", r.text); j = r.json(); d = j.get("data") or {}
            durl = d.get("durl") or []; dash = d.get("dash") or {}
            report("  playurl", plat, r.status_code, j.get("code"), j.get("message"), "durl", len(durl), "dash v", len(dash.get("video") or []), "a", len(dash.get("audio") or []), "quality", d.get("quality"), d.get("accept_description"))
            media = durl[0]["url"] if durl else ((dash.get("video") or [{}])[0].get("baseUrl"))
            if media:
                for ref in ("https://www.bilibili.com/", None):
                    h = {"Range": "bytes=0-1023"}; 
                    if ref: h["Referer"] = ref
                    m = get(media, headers=h, stream=True)
                    report("   media", plat, "referer" if ref else "no-referer", m.status_code if m is not None else None, m.headers.get("Content-Type") if m is not None else None, urllib.parse.urlparse(media).netloc)
        # vertical / story
        r = get(f"https://api.bilibili.com/x/web-interface/archive/related?bvid={bvid}", headers=hdr)
        if r is not None:
            j = r.json(); report("  related", r.status_code, j.get("code"), len(j.get("data") or []))
        r = get(f"https://api.bilibili.com/x/v2/reply/main?type=1&oid={first.get('aid')}&mode=3", headers=hdr)
        if r is not None:
            j = r.json(); report("  comments", r.status_code, j.get("code"), len(((j.get("data") or {}).get("replies") or [])))
    r = get("https://api.bilibili.com/x/web-interface/wbi/search/default", headers=hdr); 
    r = get("https://s.search.bilibili.com/main/hotword", headers=hdr)
    if r is not None: report("  hotword", r.status_code, r.text[:100])

# ---------------- Weibo ----------------
def weibo():
    report("== weibo")
    h = {"User-Agent": MUA, "Referer": "https://m.weibo.cn/", "X-Requested-With": "XMLHttpRequest", "MWeibo-Pwa": "1"}
    for name, cid in [("hot", "102803"), ("video", "102803_ctg1_9999_-_ctg1_9999_home"), ("realtimehot", "106003type%3D25%26t%3D3%26disable_hot%3D1%26filter_type%3Drealtimehot")]:
        r = get(f"https://m.weibo.cn/api/container/getIndex?containerid={cid}", headers=h)
        if r is None: continue
        save(f"weibo_{name}.json", r.text)
        try:
            j = r.json(); cards = (j.get("data") or {}).get("cards") or []
            vids = 0
            for c in cards:
                mb = c.get("mblog") or {}
                pi = mb.get("page_info") or {}
                if pi.get("type") == "video": vids += 1
            report("  ", name, r.status_code, j.get("ok"), "cards", len(cards), "videos", vids, r.text[:80].replace("\n", " "))
        except Exception as e:
            report("  ", name, r.status_code, "not json", r.text[:120].replace("\n", " "))
    # video page_info urls
    try:
        j = json.load(open(os.path.join(OUT, "weibo_hot.json"))); 
        for c in (j.get("data") or {}).get("cards") or []:
            pi = ((c.get("mblog") or {}).get("page_info") or {})
            if pi.get("type") == "video":
                urls = pi.get("urls") or {}; mi = pi.get("media_info") or {}
                u = next(iter(urls.values()), None) or mi.get("stream_url_hd") or mi.get("stream_url")
                report("   video url host", urllib.parse.urlparse(u).netloc if u else None)
                if u:
                    m = get(u, headers={"Range": "bytes=0-1023", "Referer": "https://m.weibo.cn/", "User-Agent": MUA}, stream=True)
                    report("   media", m.status_code if m is not None else None, m.headers.get("Content-Type") if m is not None else None)
                break
    except Exception as e: report("   weibo video ERR", e)
    r = get("https://m.weibo.cn/api/container/getIndex?containerid=100103type%3D1%26q%3D%E7%BE%8E%E9%A3%9F", headers=h)
    if r is not None:
        save("weibo_search.json", r.text); report("  search", r.status_code, r.text[:100].replace("\n", " "))

# ---------------- Kuaishou ----------------
def kuaishou():
    report("== kuaishou")
    r = get("https://www.kuaishou.com/new-reco"); report("  home", r.status_code if r is not None else None, len(r.text) if r is not None else 0)
    if r is not None: save("ks_home.html", r.text[:400000]); report("  apollo state", "__APOLLO_STATE__" in r.text, "photoUrl" in r.text)
    q = {"operationName": "brilliantTypeDataQuery", "variables": {"hotChannelId": "00", "page": "brilliant", "pcursor": ""}, "query": "fragment photoContent on PhotoEntity { id duration caption likeCount viewCount photoUrl coverUrl __typename } fragment feedContent on Feed { type author { id name __typename } photo { ...photoContent __typename } __typename } query brilliantTypeDataQuery($pcursor: String, $hotChannelId: String, $page: String) { brilliantTypeData(pcursor: $pcursor, hotChannelId: $hotChannelId, page: $page) { result pcursor feeds { ...feedContent __typename } __typename } }"}
    try:
        r = s.post("https://www.kuaishou.com/graphql", json=q, headers={"Referer": "https://www.kuaishou.com/brilliant", "Origin": "https://www.kuaishou.com", "Content-Type": "application/json"}, timeout=20)
        save("ks_graphql.json", r.text); report("  graphql", r.status_code, r.text[:200].replace("\n", " "))
    except Exception as e: report("  graphql ERR", e)
    r = get("https://v.kuaishou.com/", headers={"User-Agent": MUA}); report("  mobile", r.status_code if r is not None else None, r.url if r is not None else None)

# ---------------- Xiaohongshu ----------------
def xhs():
    report("== xiaohongshu")
    r = get("https://www.xiaohongshu.com/explore")
    if r is not None:
        save("xhs_explore.html", r.text[:400000])
        m = re.search(r"window.__INITIAL_STATE__=(\{.*?\})</script>", r.text, re.S)
        report("  explore", r.status_code, len(r.text), "initial state", bool(m), "noteCard" in r.text, "video" in r.text)
        if m:
            t = m.group(1).replace("undefined", "null")
            try:
                st = json.loads(t); feeds = (((st.get("feed") or {}).get("feeds")) or [])
                report("  feeds", len(feeds) if isinstance(feeds, list) else type(feeds))
            except Exception as e: report("  state parse", e)
    r = get("https://edith.xiaohongshu.com/api/sns/web/v1/homefeed/category"); report("  api", r.status_code if r is not None else None, r.text[:120] if r is not None else "")

# ---------------- Xigua / Toutiao ----------------
def xigua():
    report("== xigua")
    r = get("https://www.ixigua.com/")
    if r is not None:
        save("xigua_home.html", r.text[:400000])
        report("  home", r.status_code, len(r.text), "SSR", "_SSR_HYDRATED_DATA" in r.text, "videoResource" in r.text)
    r = get("https://www.ixigua.com/api/feedv2/feedById?channelId=94349543909&count=9&maxTime=0&request_from=701", headers={"Referer": "https://www.ixigua.com/"})
    if r is not None: save("xigua_feed.json", r.text); report("  feed api", r.status_code, r.text[:150].replace("\n", " "))

# ---------------- Douyin extras ----------------
def douyin_extra():
    report("== douyin extras (no signing)")
    r = get("https://www.iesdouyin.com/web/api/v2/hotsearch/billboard/word/", headers={"User-Agent": MUA})
    if r is not None: report("  billboard", r.status_code, r.text[:120])

for f in (bili, weibo, kuaishou, xhs, xigua, douyin_extra):
    try: f()
    except Exception as e: report("ERR", f.__name__, e)
