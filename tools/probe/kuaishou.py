"""Kuaishou as a visitor: feed channels, search, a file. Writes out/ks_*"""
import json, os, sys, requests
OUT = sys.argv[1] if len(sys.argv) > 1 else "out"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
s = requests.Session(); s.headers["User-Agent"] = UA
H = {"Referer": "https://www.kuaishou.com/brilliant", "Origin": "https://www.kuaishou.com", "Content-Type": "application/json"}
def p(*a): print(*a, flush=True)
def gql(name, q, v, ref=None):
    h = dict(H); 
    if ref: h["Referer"] = ref
    r = s.post("https://www.kuaishou.com/graphql", json={"operationName": name, "variables": v, "query": q}, headers=h, timeout=20)
    open(os.path.join(OUT, f"ks_{name}.json"), "w").write(r.text)
    return r
s.get("https://www.kuaishou.com/new-reco", timeout=20)
p("cookies", list(s.cookies.keys()))
FEED = "fragment photoContent on PhotoEntity { id duration caption likeCount viewCount photoUrl coverUrl timestamp __typename } fragment feedContent on Feed { type author { id name headerUrl __typename } photo { ...photoContent __typename } tags { type name __typename } __typename } query brilliantTypeDataQuery($pcursor: String, $hotChannelId: String, $page: String) { brilliantTypeData(pcursor: $pcursor, hotChannelId: $hotChannelId, page: $page) { result pcursor feeds { ...feedContent __typename } __typename } }"
for ch in ("00", "11", "12", "13"):
    r = gql("brilliantTypeDataQuery", FEED, {"hotChannelId": ch, "page": "brilliant", "pcursor": ""})
    try:
        d = r.json()["data"]["brilliantTypeData"]; f = d.get("feeds") or []
        p("feed", ch, r.status_code, d.get("result"), len(f), d.get("pcursor"), [ (x.get("photo") or {}).get("caption","")[:15] for x in f[:3]], "tags", [(x.get("tags") or [])[:2] for x in f[:2]])
    except Exception as e: p("feed", ch, r.status_code, r.text[:200])
# next page
try:
    r = gql("brilliantTypeDataQuery", FEED, {"hotChannelId": "00", "page": "brilliant", "pcursor": "1"}); d = r.json()["data"]["brilliantTypeData"]; p("feed page2", d.get("result"), len(d.get("feeds") or []), d.get("pcursor"))
except Exception as e: p("page2 err", e)
# file
try:
    f = json.load(open(os.path.join(OUT, "ks_brilliantTypeDataQuery.json")))["data"]["brilliantTypeData"]["feeds"]
    u = f[0]["photo"]["photoUrl"]
    for ref in ("https://www.kuaishou.com/", None):
        h = {"Range": "bytes=0-1023"}
        if ref: h["Referer"] = ref
        m = s.get(u, headers=h, timeout=20, stream=True); p("file", "ref" if ref else "noref", m.status_code, m.headers.get("Content-Type"), u.split("/")[2])
    c = f[0]["photo"]["coverUrl"]; m = s.get(c, timeout=20); p("cover", m.status_code, m.headers.get("Content-Type"))
except Exception as e: p("file err", e)
# search
SEARCH = "fragment photoContent on PhotoEntity { id duration caption likeCount viewCount photoUrl coverUrl timestamp __typename } fragment feedContent on Feed { type author { id name __typename } photo { ...photoContent __typename } __typename } query visionSearchPhoto($keyword: String, $pcursor: String, $searchSessionId: String, $page: String, $webPageArea: String) { visionSearchPhoto(keyword: $keyword, pcursor: $pcursor, searchSessionId: $searchSessionId, page: $page, webPageArea: $webPageArea) { result llsid webPageArea feeds { ...feedContent __typename } searchSessionId pcursor aladdinBanner { imgUrl link __typename } __typename } }"
r = gql("visionSearchPhoto", SEARCH, {"keyword": "美食", "pcursor": "", "page": "search"}, ref="https://www.kuaishou.com/search/video?searchKey=%E7%BE%8E%E9%A3%9F")
p("search", r.status_code, r.text[:300])
# related / profile
try:
    f = json.load(open(os.path.join(OUT, "ks_brilliantTypeDataQuery.json")))["data"]["brilliantTypeData"]["feeds"]
    uid = f[0]["author"]["id"]; pid = f[0]["photo"]["id"]
    PROF = "fragment photoContent on PhotoEntity { id duration caption photoUrl coverUrl __typename } fragment feedContent on Feed { type photo { ...photoContent __typename } __typename } query visionProfilePhotoList($pcursor: String, $userId: String, $page: String, $webPageArea: String) { visionProfilePhotoList(pcursor: $pcursor, userId: $userId, page: $page, webPageArea: $webPageArea) { result llsid webPageArea feeds { ...feedContent __typename } hostName pcursor __typename } }"
    r = gql("visionProfilePhotoList", PROF, {"userId": uid, "pcursor": "", "page": "profile"}, ref=f"https://www.kuaishou.com/profile/{uid}")
    p("profile", r.status_code, r.text[:300])
except Exception as e: p("profile err", e)
