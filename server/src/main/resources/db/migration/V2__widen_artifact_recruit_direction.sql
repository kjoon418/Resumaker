-- QA 2026-06-21 #1·#2: 산출물 목표 스냅샷의 채용 방향 길이 제한을 200 → 5000으로 확장한다.
-- 목표(TargetBrief.RecruitDirection)는 이미 5000자(채용공고 전문 수용)를 허용하나, 생성 시점에 만들어지는
-- 산출물 스냅샷 컬럼이 VARCHAR(200)이라 5000자에 가까운 채용방향으로 이력서·포트폴리오를 만들 수 없었다.
-- 스냅샷은 원본 채용방향을 그대로 복제하므로 원본과 동일한 상한을 둔다(ArtifactTargetSnapshot.MAX_DIRECTION_LENGTH).
ALTER TABLE public.artifacts
    ALTER COLUMN target_recruit_direction TYPE character varying(5000);
