-- V3 비동기 생성 작업 스키마. 산출물 AI 생성을 동기 블로킹에서 GenerationJob 기반 비동기로 전환하기 위한
-- 작업 큐 테이블이다. 엔티티(GenerationJob) @Column/@CollectionTable 매핑과 1:1로 맞춘다(운영 ddl-auto=validate).

CREATE TABLE public.generation_jobs (
    id uuid NOT NULL,
    owner_id uuid NOT NULL,
    kind character varying(255) NOT NULL,
    target_id uuid NOT NULL,
    template_id uuid,
    target_company character varying(255),
    status character varying(255) NOT NULL,
    artifact_id uuid,
    error_code character varying(255),
    error_message character varying(2000),
    attempts integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    started_at timestamp(6) with time zone,
    finished_at timestamp(6) with time zone,
    CONSTRAINT generation_jobs_kind_check CHECK (((kind)::text = ANY ((ARRAY['RESUME'::character varying, 'PORTFOLIO'::character varying])::text[]))),
    CONSTRAINT generation_jobs_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'RUNNING'::character varying, 'SUCCEEDED'::character varying, 'FAILED'::character varying])::text[])))
);

CREATE TABLE public.generation_job_experience_ids (
    generation_job_id uuid NOT NULL,
    experience_id uuid NOT NULL,
    experience_id_order integer NOT NULL
);

ALTER TABLE ONLY public.generation_jobs
    ADD CONSTRAINT generation_jobs_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.generation_job_experience_ids
    ADD CONSTRAINT generation_job_experience_ids_pkey PRIMARY KEY (generation_job_id, experience_id_order);

ALTER TABLE ONLY public.generation_job_experience_ids
    ADD CONSTRAINT fk_generation_job_experience_ids_job FOREIGN KEY (generation_job_id) REFERENCES public.generation_jobs(id);
