import { del, get, post, put } from './http';
import { isMockEnabled } from './mock';
import { adminMock } from './admin-mock';
import type { PageResult } from '../types/api';
import type { Candidate, Offer, Requisition } from '../types/admin';

/* ---------------------------- 职位需求 ---------------------------- */

export function listRequisitions(page: number, size: number): Promise<PageResult<Requisition>> {
  if (isMockEnabled()) return adminMock.listRequisitions(page, size);
  return get<PageResult<Requisition>>('/rcu/requisitions', { page, size });
}

export function createRequisition(data: Record<string, unknown>): Promise<Requisition> {
  if (isMockEnabled()) return adminMock.createRequisition(data as any);
  return post<Requisition>('/rcu/requisitions', data);
}

export function updateRequisition(id: number, data: Record<string, unknown>): Promise<Requisition> {
  if (isMockEnabled()) return adminMock.updateRequisition(id, data as any);
  return put<Requisition>(`/rcu/requisitions/${id}`, data);
}

export function deleteRequisition(id: number): Promise<void> {
  if (isMockEnabled()) return adminMock.removeRequisition(id);
  return del<void>(`/rcu/requisitions/${id}`);
}

/* ---------------------------- 候选人 ---------------------------- */

export function listCandidates(page: number, size: number): Promise<PageResult<Candidate>> {
  if (isMockEnabled()) return adminMock.listCandidates(page, size);
  return get<PageResult<Candidate>>('/rcu/candidates', { page, size });
}

export function createCandidate(data: Record<string, unknown>): Promise<Candidate> {
  if (isMockEnabled()) return adminMock.createCandidate(data as any);
  return post<Candidate>('/rcu/candidates', data);
}

export function updateCandidate(id: number, data: Record<string, unknown>): Promise<Candidate> {
  if (isMockEnabled()) return adminMock.updateCandidate(id, data as any);
  return put<Candidate>(`/rcu/candidates/${id}`, data);
}

export function deleteCandidate(id: number): Promise<void> {
  if (isMockEnabled()) return adminMock.removeCandidate(id);
  return del<void>(`/rcu/candidates/${id}`);
}

/* ---------------------------- Offer ---------------------------- */

export function listOffers(page: number, size: number): Promise<PageResult<Offer>> {
  if (isMockEnabled()) return adminMock.listOffers(page, size);
  return get<PageResult<Offer>>('/rcu/offers', { page, size });
}

export function createOffer(data: Record<string, unknown>): Promise<Offer> {
  if (isMockEnabled()) return adminMock.createOffer(data as any);
  return post<Offer>('/rcu/offers', data);
}

export function updateOffer(id: number, data: Record<string, unknown>): Promise<Offer> {
  if (isMockEnabled()) return adminMock.updateOffer(id, data as any);
  return put<Offer>(`/rcu/offers/${id}`, data);
}

export function deleteOffer(id: number): Promise<void> {
  if (isMockEnabled()) return adminMock.removeOffer(id);
  return del<void>(`/rcu/offers/${id}`);
}

/** 录用建档：Offer 转员工预建档（跨域联动），返回新员工 ID */
export function onboardOffer(id: number): Promise<number> {
  if (isMockEnabled()) return adminMock.onboardOffer(id);
  return post<number>(`/rcu/offers/${id}/onboard`);
}
