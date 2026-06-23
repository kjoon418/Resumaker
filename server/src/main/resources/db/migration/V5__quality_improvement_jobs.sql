-- V5 품질 개선(자동 처치) 스키마. 진단(품질 점검)은 휘발이라 영속하지 않고, 비동기 처치 작업과 그 항목 후보만
-- 영속한다(기획 §4.3). 엔티티(QualityImprovementJob·QualityCandidate) @Column/@CollectionTable 매핑과 1:1로 맞춘다
-- (운영 ddl-auto=validate). V3 generation_jobs 패턴을 따른다.

CREATE TABLE public.quality_improvement_jobs (
    id uuid NOT NULL,
    owner_id uuid NOT NULL,
    artifact_id uuid NOT NULL,
    version_id uuid NOT NULL,
    status character varying(255) NOT NULL,
    error_code character varying(255),
    error_message character varying(2000),
    attempts integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    started_at timestamp(6) with time zone,
    finished_at timestamp(6) with time zone,
    CONSTRAINT quality_improvement_jobs_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RUNNING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE public.quality_improvement_job_finding_ids (
    quality_improvement_job_id uuid NOT NULL,
    finding_id character varying(255) NOT NULL,
    finding_id_order integer NOT NULL
);

CREATE TABLE public.quality_candidates (
    id uuid NOT NULL,
    job_id uuid NOT NULL,
    section_id uuid NOT NULL,
    definition_key character varying(100) NOT NULL,
    original_content character varying(10000) NOT NULL,
    candidate_content character varying(10000) NOT NULL
);

CREATE TABLE public.quality_candidate_criterion_ids (
    quality_candidate_id uuid NOT NULL,
    criterion_id character varying(32) NOT NULL,
    criterion_id_order integer NOT NULL
);

ALTER TABLE ONLY public.quality_improvement_jobs
    ADD CONSTRAINT quality_improvement_jobs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.quality_improvement_job_finding_ids
    ADD CONSTRAINT quality_improvement_job_finding_ids_pkey PRIMARY KEY (quality_improvement_job_id, finding_id_order);

ALTER TABLE ONLY public.quality_improvement_job_finding_ids
    ADD CONSTRAINT fk_quality_improvement_job_finding_ids_job FOREIGN KEY (quality_improvement_job_id) REFERENCES public.quality_improvement_jobs(id);

ALTER TABLE ONLY public.quality_candidates
    ADD CONSTRAINT quality_candidates_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.quality_candidate_criterion_ids
    ADD CONSTRAINT quality_candidate_criterion_ids_pkey PRIMARY KEY (quality_candidate_id, criterion_id_order);

ALTER TABLE ONLY public.quality_candidate_criterion_ids
    ADD CONSTRAINT fk_quality_candidate_criterion_ids_candidate FOREIGN KEY (quality_candidate_id) REFERENCES public.quality_candidates(id);

CREATE INDEX idx_quality_candidates_job_id ON public.quality_candidates (job_id);
