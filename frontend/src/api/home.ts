import request from '@/utils/request';

/**
 * Career-related Bilibili video card. The home page renders the cover, an
 * overlaid duration badge, and the UP-name; tapping opens
 * {@code https://www.bilibili.com/video/<bvid>} via the shared {@code openLink}
 * util (web-view if {@code *.bilibili.com} is on the MP business-domain
 * whitelist, otherwise clipboard fallback).
 */
export interface BiliVideoCard {
  id: number;
  bvid: string;
  title: string;
  coverUrl?: string;
  upName?: string;
  durationSec?: number;
  viewCount?: number;
  keyword?: string;
  url: string;
}

/**
 * Career article shown on the home feed. The {@code url} field can be either
 * an in-app route ({@code /pages/...}) or an external https URL — the
 * {@code openLink} util handles both.
 */
export interface HomeArticle {
  id: number;
  title: string;
  summary?: string;
  imageUrl?: string;
  thumbnailUrl?: string;
  sourceIconUrl?: string;
  url?: string;
  sourceUrl?: string;
  category?: string;
}

/**
 * Editor-curated career consultation / tip card. Body is short markdown
 * suitable for inline rendering as a small list.
 */
export interface HomeConsultation {
  id: number;
  title: string;
  body?: string;
  author?: string;
  imageUrl?: string;
  sourceUrl?: string;
}

export interface CareerCard {
  pathId: number;
  name: string;
  description: string;
}

export interface HomeStats {
  totalCareerPaths: number;
  totalUsers: number;
  totalInterviews: number;
}

export interface HomepageFeedResponse {
  videos: BiliVideoCard[];
  articles: HomeArticle[];
  consultations: HomeConsultation[];
  careerCards: CareerCard[];
  stats: HomeStats;
}

/**
 * Public preview uses a non-personalized feed. A signed-in account can opt
 * into the personalized route, where the backend derives identity solely
 * from the JWT instead of trusting a caller-supplied userId.
 */
export const getHomeContentApi = (personalized = false) => {
  return request<HomepageFeedResponse>({
    url: personalized ? '/api/homepage/feed/personalized' : '/api/homepage/feed',
    method: 'GET',
    silent: true,
  });
};

export const refreshHomeContentApi = () => {
  return request<string>({
    url: '/api/homepage/refresh',
    method: 'POST',
    silent: true,
  });
};
