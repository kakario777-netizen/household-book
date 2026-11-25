package com.kakario.householdbook.data

/**
 * 가게 이름으로 분류 자동 지정.
 * 사용자가 고친 분류(가게별 규칙)가 먼저, 그다음 아래 기본 단어 목록 (긴 단어부터 비교).
 */
object Categorizer {

    private val RAW = mapOf(
        "dining" to "배달의민족 배민 우아한형제들 요기요 쿠팡이츠 땡겨요 식당 김밥 치킨 피자 버거 맥도날드 롯데리아 버거킹 맘스터치 " +
            "서브웨이 국밥 돈까스 돈가스 분식 반점 짬뽕 짜장 중화 고기 갈비 삼겹 횟집 회센터 초밥 스시 족발 보쌈 곱창 막창 포차 주점 호프 " +
            "냉면 칼국수 해장국 순대 감자탕 레스토랑 뷔페 아웃백 빕스 교촌 bhc bbq 굽네 도미노 파스타 쌀국수 우동 라멘 덮밥 " +
            "닭갈비 찜닭 떡볶이 엽기 신전 한식 양식 일식 이자카야 정육식당 식육식당 밥집 푸드",
        "cafe" to "스타벅스 이디야 투썸 메가커피 메가엠지씨 컴포즈 빽다방 커피 카페 cafe coffee 파리바게뜨 뚜레쥬르 베이커리 제과 " +
            "배스킨 던킨 설빙 공차 할리스 폴바셋 탐앤탐스 매머드 더벤티 아이스크림 디저트 도넛 " +
            "cu씨유 씨유 gs25 지에스25 세븐일레븐 이마트24 미니스톱 편의점 코리아세븐 비지에프 bgf",
        "grocery" to "이마트 홈플러스 롯데마트 하나로 농협 마트 식자재 정육 수산 청과 과일 야채 채소 반찬 마켓컬리 컬리 " +
            "gs더프레시 노브랜드 트레이더스 코스트코 슈퍼 탑마트 메가마트 축산 떡집 방앗간",
        "transport" to "주유 오일 sk에너지 gs칼텍스 s-oil 에쓰오일 현대오일뱅크 충전소 lpg 택시 카카오t 카카오모빌리티 티머니 " +
            "버스 지하철 코레일 ktx srt 고속버스 시외버스 주차 하이패스 톨게이트 도로공사 세차 정비 타이어 카센터 오토",
        "shopping" to "쿠팡 네이버페이 11번가 g마켓 지마켓 옥션 무신사 다이소 올리브영 백화점 아울렛 신세계 롯데쇼핑 현대백화점 " +
            "알리익스프레스 테무 위메프 티몬 ssg 에이블리 지그재그 유니클로 탑텐 스파오 나이키 아디다스 하이마트 전자랜드 " +
            "이케아 오늘의집 의류 신발 안경",
        "subscribe" to "skt sk텔레콤 kt 케이티 lgu+ 유플러스 엘지유플러스 통신 알뜰폰 넷플릭스 netflix 유튜브 youtube google 구글 " +
            "애플 apple 디즈니 티빙 웨이브 왓챠 멜론 지니뮤직 스포티파이 spotify claude anthropic openai chatgpt 밀리의서재 " +
            "쿠팡와우 네이버플러스 icloud 마이크로소프트 microsoft adobe",
        "living" to "전기 가스 수도 관리비 한국전력 한전 도시가스 보험 세탁 미용 헤어 이발 바버 네일 철물 세무 국세 지방세 " +
            "아파트 생활용품 꽃 우체국 택배 렌탈 정수기 코웨이 쿠쿠",
        "medical" to "병원 의원 약국 치과 한의원 내과 소아과 이비인후과 피부과 안과 정형외과 외과 산부인과 비뇨 재활 의료 " +
            "메디컬 클리닉 검진",
        "edu" to "학원 교육 서점 교보문고 영풍문고 알라딘 예스24 yes24 문구 키즈 유치원 어린이집 장난감 토이 학습지 " +
            "구몬 눈높이 아트박스 레고",
        "leisure" to "cgv 메가박스 롯데시네마 영화 pc방 피씨방 노래 코인노래 볼링 당구 게임 스팀 steam 닌텐도 nintendo " +
            "플레이스테이션 playstation 여행 호텔 모텔 펜션 야놀자 여기어때 숙박 놀이 테마파크 키즈카페 워터파크 " +
            "골프 헬스 피트니스 수영 캠핑 낚시 공연 티켓 인터파크",
    )

    /** (단어, 분류) — 긴 단어부터 */
    private val WORDS: List<Pair<String, String>> by lazy {
        RAW.flatMap { (cat, words) -> words.split(" ").filter { it.isNotBlank() }.map { normMerchant(it) to cat } }
            .sortedByDescending { it.first.length }
    }

    /** rules: 정규화된 가게 이름 → 분류 id (사용자가 고친 것) */
    fun categorize(merchant: String, kind: String, rules: Map<String, String>): String {
        val n = normMerchant(merchant)
        if (n.isNotEmpty()) {
            rules[n]?.let { return it }
            for ((w, cat) in WORDS) if (w.isNotEmpty() && n.contains(w)) return cat
        }
        if (kind == "자동납부") return "living"
        return "etc"
    }
}
