-- V4 AI 작성 전략(Writing Strategy) 스키마. 목표(target_briefs)에 채용 방향에서 추출한 작성 전략(JSON)과 추출
-- 상태를 추가하고, 산출물(artifacts) 목표 스냅샷에 생성 시점의 작성 전략 JSON을 함께 보존한다. 엔티티
-- (TargetBrief·ArtifactTargetSnapshot) @Column 매핑과 1:1로 맞춘다(운영 ddl-auto=validate).

-- 목표: 작성 전략 JSON(nullable text)·추출 상태(not null, 기존 행은 default 'PENDING'으로 백필 → 워커가 추출)·
-- 추출 시작 시각(nullable, 고아 EXTRACTING 회수 기준).
ALTER TABLE public.target_briefs
    ADD COLUMN writing_strategy text,
    ADD COLUMN strategy_status character varying(255) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN strategy_extraction_started_at timestamp(6) with time zone;

ALTER TABLE public.target_briefs
    ADD CONSTRAINT target_briefs_strategy_status_check
    CHECK (((strategy_status)::text = ANY ((ARRAY['PENDING'::character varying, 'EXTRACTING'::character varying, 'READY'::character varying, 'FAILED'::character varying])::text[])));

-- 산출물 목표 스냅샷: 생성에 쓴 작성 전략 JSON(nullable text — 원문 폴백이면 null). 기존 산출물은 null로 백필된다.
ALTER TABLE public.artifacts
    ADD COLUMN target_writing_strategy text;
