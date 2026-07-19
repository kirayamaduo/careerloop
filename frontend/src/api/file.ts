import { uploadFileRequest } from '@/utils/request';

/**
 * Upload a file to OSS via the backend.
 *
 * The backend returns a per-account object key (e.g. `resumes/42/uuid.pdf`), NOT a
 * publicly loadable URL. Persist the key with the entity (resume row,
 * user.avatarUrl, etc.) and call `getFileViewUrlApi` if you ever need a
 * short-lived signed URL outside of the normal entity-fetch flow — most
 * pages will get one for free in the entity's `*ViewUrl` field.
 *
 * @param filePath local file path (from uni.chooseFile / chooseImage)
 * @param folder   OSS folder prefix (default: `resumes`)
 * @returns OSS object key
 */
export const uploadFileApi = (filePath: string, folder: string = 'resumes'): Promise<string> => {
  return uploadFileRequest<string>({
    url: '/api/files/upload',
    filePath,
    name: 'file',
    formData: { folder },
  });
};
